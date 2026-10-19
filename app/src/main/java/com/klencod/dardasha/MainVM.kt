package com.klencod.dardasha

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.klencod.dardasha.data.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant

sealed interface Route {
    data object Home : Route
    data class Conversation(val peer: String) : Route
    data object NewChat : Route
    data object Me : Route
    data object Archived : Route
    data object Starred : Route
}

private const val MAX_PINS = 3

class MainVM(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val prefs = ctx.getSharedPreferences("dardasha", 0)

    // ---------- state ----------
    var meId by mutableStateOf<String?>(null); private set
    var booting by mutableStateOf(true); private set
    var route by mutableStateOf<Route>(Route.Home)
    var conn by mutableStateOf("")
    var toast by mutableStateOf<String?>(null)
    var authBusy by mutableStateOf(false); private set

    val msgs = mutableStateListOf<Message>()
    val peers = mutableStateMapOf<String, Profile>()
    val nicks = mutableStateMapOf<String, String>()
    val reactions = mutableStateListOf<Reaction>()
    val typingFrom = mutableStateMapOf<String, Boolean>()
    val seenOverride = mutableStateMapOf<String, String>()
    var blocked by mutableStateOf<Set<String>>(emptySet()); private set
    var online by mutableStateOf<Set<String>>(emptySet()); private set
    var hidden by mutableStateOf(prefs.getStringSet("hidden", emptySet())?.toSet() ?: emptySet()); private set
    val chatPrefs = mutableStateMapOf<String, ChatPref>()
    var starred by mutableStateOf<Set<String>>(emptySet()); private set
    private var timers = mapOf<String, Int>()

    private val seen = HashSet<String>()
    private val jobs = mutableListOf<Job>()
    private val channels = mutableListOf<RealtimeChannel>()
    private var room: RealtimeChannel? = null
    private val typJobs = HashMap<String, Job>()
    private var lastTypingSent = 0L
    private val readSent = HashSet<String>()

    init {
        viewModelScope.launch {
            Supa.client.auth.sessionStatus.collect { st ->
                when (st) {
                    is SessionStatus.Authenticated -> st.session.user?.id?.let { onLogin(it) }
                    is SessionStatus.NotAuthenticated -> onLogout()
                    else -> Unit
                }
            }
        }
    }

    // ---------- auth ----------
    fun signIn(email: String, pass: String, onErr: (String) -> Unit) = viewModelScope.launch {
        authBusy = true
        runCatching { Repo.signIn(email, pass) }.onFailure { onErr(trErr(it.message)) }
        authBusy = false
    }

    fun signUp(email: String, pass: String, user: String, onErr: (String) -> Unit, onConfirm: () -> Unit) =
        viewModelScope.launch {
            authBusy = true
            runCatching { Repo.signUp(email, pass, user) }
                .onSuccess { if (!it) onConfirm() }
                .onFailure { onErr(trErr(it.message)) }
            authBusy = false
        }

    fun signOut() = viewModelScope.launch { runCatching { Repo.signOut() } }

    private fun trErr(m: String?): String {
        val s = m ?: return "حدث خطأ"
        return when {
            Regex("already registered", RegexOption.IGNORE_CASE).containsMatchIn(s) -> "الإيميل مسجّل من قبل"
            Regex("Invalid login", RegexOption.IGNORE_CASE).containsMatchIn(s) -> "الإيميل أو كلمة المرور غير صحيحة"
            Regex("duplicate|unique", RegexOption.IGNORE_CASE).containsMatchIn(s) -> "اسم المستخدم محجوز"
            Regex("Database error", RegexOption.IGNORE_CASE).containsMatchIn(s) -> "اسم المستخدم محجوز أو غير صالح"
            Regex("not confirmed", RegexOption.IGNORE_CASE).containsMatchIn(s) -> "أكّد الإيميل أولاً"
            else -> s
        }
    }

    private fun onLogin(id: String) {
        if (meId == id) { booting = false; return }
        stopSession()
        meId = id
        booting = false
        route = Route.Home
        jobs += viewModelScope.launch { bootstrap(id) }
    }

    private fun onLogout() {
        stopSession()
        meId = null
        booting = false
        route = Route.Home
    }

    private fun stopSession() {
        jobs.forEach { it.cancel() }; jobs.clear()
        typJobs.values.forEach { it.cancel() }; typJobs.clear()
        val chs = channels.toList(); channels.clear()
        val r = room; room = null
        viewModelScope.launch {
            chs.forEach { runCatching { Supa.client.realtime.removeChannel(it) } }
            r?.let { runCatching { Supa.client.realtime.removeChannel(it) } }
        }
        msgs.clear(); seen.clear(); peers.clear(); nicks.clear(); reactions.clear()
        typingFrom.clear(); seenOverride.clear(); readSent.clear()
        chatPrefs.clear(); starred = emptySet()
        blocked = emptySet(); online = emptySet(); timers = emptyMap()
        conn = ""
    }

    // ---------- bootstrap ----------
    private suspend fun bootstrap(id: String) {
        conn = "○ جارٍ الاتصال..."
        val r = runCatching { Repo.loadMessages() }
        if (r.isFailure) {
            conn = "○ بانتظار الشبكة"
            toast = "تعذّر التحميل: " + (r.exceptionOrNull()?.message ?: "")
            return
        }
        r.getOrThrow().asReversed().forEach { addMsg(it) }
        runCatching { Repo.loadContacts() }.onSuccess { l -> l.forEach { nicks[it.contact] = it.nick ?: "" } }
        runCatching { Repo.loadBlocks() }.onSuccess { l -> blocked = l.map { it.blocked }.toSet() }
        runCatching { Repo.loadReactions() }.onSuccess { reactions.clear(); reactions.addAll(it) }
        runCatching { Repo.loadTimers() }.onSuccess { l ->
            timers = l.associate { (if (it.a == id) it.b else it.a) to (it.secs ?: 0) }
        }
        loadLocalExtras(id)
        runCatching { Repo.loadPrefs() }.onSuccess { l ->
            chatPrefs.clear(); l.forEach { chatPrefs[it.peer] = it }; saveLocalPrefs(id)
        }
        runCatching { Repo.loadStars() }.onSuccess { l ->
            starred = l.map { it.messageId }.toSet(); saveLocalStars(id)
        }
        loadPeers(id)
        subscribeMessages(id)
        subscribeReactions()
        subscribeRoom(id)
        jobs += viewModelScope.launch {
            while (true) {
                delay(30_000)
                if (AppState.foreground) runCatching { Repo.touchLastSeen(id, Instant.now().toString()) }
            }
        }
    }

    // ---------- pin / archive / mute / stars (محلياً + مزامنة مع Supabase إن وُجد الجدول) ----------
    private fun loadLocalExtras(id: String) {
        runCatching {
            prefs.getString("cp_$id", null)?.let { js ->
                AppJson.decodeFromString<List<ChatPref>>(js).forEach { chatPrefs[it.peer] = it }
            }
        }
        starred = prefs.getStringSet("st_$id", emptySet())?.toSet() ?: emptySet()
    }

    private fun saveLocalPrefs(id: String) {
        runCatching { prefs.edit().putString("cp_$id", AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(ChatPref.serializer()), chatPrefs.values.toList())).apply() }
    }

    private fun saveLocalStars(id: String) {
        prefs.edit().putStringSet("st_$id", starred).apply()
    }

    fun pref(peer: String) = chatPrefs[peer] ?: ChatPref(peer)
    fun isPinned(peer: String) = pref(peer).pinned
    fun isArchived(peer: String) = pref(peer).archived
    fun isMuted(peer: String) = pref(peer).muted

    private fun updatePref(peer: String, f: (ChatPref) -> ChatPref) {
        val me = meId ?: return
        val np = f(pref(peer))
        if (np == ChatPref(peer)) chatPrefs.remove(peer) else chatPrefs[peer] = np
        saveLocalPrefs(me)
        viewModelScope.launch { runCatching { Repo.savePref(me, np) } }
    }

    fun togglePin(peer: String) {
        if (!isPinned(peer) && chatPrefs.values.count { it.pinned } >= MAX_PINS) {
            toast = "يمكن تثبيت $MAX_PINS محادثات كحد أقصى"; return
        }
        val on = !isPinned(peer)
        updatePref(peer) { it.copy(pinned = on) }
        toast = if (on) "تم تثبيت المحادثة" else "تم إلغاء التثبيت"
    }

    fun toggleArchive(peer: String) {
        val on = !isArchived(peer)
        // الأرشفة تلغي التثبيت (مثل واتساب)
        updatePref(peer) { it.copy(archived = on, pinned = if (on) false else it.pinned) }
        toast = if (on) "تمت أرشفة المحادثة" else "تم إلغاء الأرشفة"
    }

    fun toggleMute(peer: String) {
        val on = !isMuted(peer)
        updatePref(peer) { it.copy(muted = on) }
        toast = if (on) "تم كتم المحادثة" else "تم إلغاء الكتم"
    }

    fun isStarred(id: String) = id in starred

    fun toggleStar(m: Message) {
        val me = meId ?: return
        val on = m.id !in starred
        starred = if (on) starred + m.id else starred - m.id
        saveLocalStars(me)
        viewModelScope.launch {
            runCatching { if (on) Repo.addStar(me, m.id) else Repo.removeStar(me, m.id) }
        }
        toast = if (on) "تم تمييز الرسالة بنجمة" else "أُزيلت النجمة"
    }

    fun starredMessages(): List<Message> =
        msgs.filter { it.id in starred && visible(it) }.sortedByDescending { it.createdAt }

    private suspend fun loadPeers(id: String) {
        val need = (msgs.map { other(it) } + nicks.keys + id).toSet().filter { it !in peers }
        runCatching { Repo.profiles(need) }.onSuccess { l -> l.forEach { peers[it.id] = it } }
    }

    private fun addMsg(m: Message): Boolean {
        if (!seen.add(m.id)) return false
        val exp = parseIso(m.expiresAt)
        if (exp != null && exp <= System.currentTimeMillis()) return false
        msgs.add(m)
        return true
    }

    // ---------- realtime ----------
    private suspend fun subscribeMessages(me: String) {
        val ch = Supa.client.channel("msgs")
        val ins = ch.postgresChangeFlow<PostgresAction.Insert>("public") { table = "messages" }
        val upd = ch.postgresChangeFlow<PostgresAction.Update>("public") { table = "messages" }
        val del = ch.postgresChangeFlow<PostgresAction.Delete>("public") { table = "messages" }
        channels += ch
        jobs += viewModelScope.launch {
            ins.collect { a ->
                val m = runCatching { a.decodeRecord<Message>() }.getOrNull() ?: return@collect
                if (m.sender != me && m.receiver != me) return@collect
                if (addMsg(m)) {
                    if (other(m) !in peers) loadPeers(me)
                    if (m.sender != me) onIncoming(m)
                }
            }
        }
        jobs += viewModelScope.launch {
            upd.collect { a ->
                val m = runCatching { a.decodeRecord<Message>() }.getOrNull() ?: return@collect
                val i = msgs.indexOfFirst { it.id == m.id }
                if (i >= 0) msgs[i] = m
            }
        }
        jobs += viewModelScope.launch {
            del.collect { a ->
                val id = a.oldRecord["id"]?.jsonPrimitive?.content ?: return@collect
                msgs.removeAll { it.id == id }
            }
        }
        runCatching { ch.subscribe(blockUntilSubscribed = true) }
            .onSuccess { conn = "● متصل" }
            .onFailure { conn = "○ بانتظار الشبكة" }
    }

    private suspend fun subscribeReactions() {
        val ch = Supa.client.channel("rx")
        val f = ch.postgresChangeFlow<PostgresAction>("public") { table = "message_reactions" }
        channels += ch
        jobs += viewModelScope.launch {
            f.collect { runCatching { Repo.loadReactions() }.onSuccess { l -> reactions.clear(); reactions.addAll(l) } }
        }
        runCatching { ch.subscribe(blockUntilSubscribed = true) }
    }

    private suspend fun subscribeRoom(me: String) {
        // نفس قناة "room" في نسخة الويب: presence للمتصلين + broadcast للكتابة
        val r = Supa.client.channel("room") { presence { key = me } }
        val pres = r.presenceChangeFlow()
        val typ = r.broadcastFlow<JsonObject>("typing")
        room = r
        jobs += viewModelScope.launch {
            pres.collect { a ->
                online = (online + a.joins.keys) - a.leaves.keys
                a.leaves.keys.forEach { seenOverride[it] = Instant.now().toString(); typingFrom.remove(it) }
            }
        }
        jobs += viewModelScope.launch {
            typ.collect { p ->
                if (p["to"]?.jsonPrimitive?.content != me) return@collect
                val from = p["from"]?.jsonPrimitive?.content ?: return@collect
                val on = runCatching { p["on"]?.jsonPrimitive?.boolean }.getOrNull() == true
                typJobs[from]?.cancel()
                if (on) {
                    typingFrom[from] = true
                    typJobs[from] = viewModelScope.launch { delay(4000); typingFrom.remove(from) }
                } else typingFrom.remove(from)
            }
        }
        runCatching {
            r.subscribe(blockUntilSubscribed = true)
            r.track(buildJsonObject { put("at", System.currentTimeMillis()) })
            Repo.touchLastSeen(me, Instant.now().toString())
        }
    }

    fun onForeground() {
        val me = meId ?: return
        val r = room ?: return
        viewModelScope.launch {
            runCatching {
                r.track(buildJsonObject { put("at", System.currentTimeMillis()) })
                Repo.touchLastSeen(me, Instant.now().toString())
            }
        }
    }

    private fun onIncoming(m: Message) {
        if (m.sender in blocked) return
        // رسالة جديدة تُخرج المحادثة من الأرشيف ما لم تكن مكتومة
        if (isArchived(m.sender) && !isMuted(m.sender)) updatePref(m.sender) { it.copy(archived = false) }
        if (isMuted(m.sender)) return
        val viewing = (route as? Route.Conversation)?.peer == m.sender
        if (viewing && AppState.foreground) return
        if (!AppState.foreground) {
            Notifier.show(ctx, m.sender, name(m.sender), preview(m))
        }
    }

    // ---------- helpers ----------
    fun other(m: Message) = if (m.sender == meId) m.receiver else m.sender
    fun name(id: String) = nicks[id]?.takeIf { it.isNotBlank() } ?: peers[id]?.username ?: "…"
    fun isOnline(id: String) = id in online

    fun preview(m: Message): String = when {
        m.isDeleted -> "🚫 تم حذف هذه الرسالة"
        m.audioPath != null -> "🎤 رسالة صوتية"
        m.filePath != null -> "📎 " + (m.fileName ?: "ملف")
        parseGeo(m.text) != null -> "📍 موقع"
        m.imagePath != null && m.text.isBlank() -> "📷 صورة"
        else -> m.text
    }

    private fun visible(m: Message): Boolean {
        if (m.id in hidden) return false
        val exp = parseIso(m.expiresAt)
        return exp == null || exp > System.currentTimeMillis()
    }

    fun thread(peer: String): List<Message> =
        msgs.filter { other(it) == peer && visible(it) }.sortedBy { it.createdAt }

    /** آخر رسالة لكل محادثة، الأحدث أولاً */
    fun conversations(): List<Pair<String, Message>> {
        val by = LinkedHashMap<String, Message>()
        msgs.sortedBy { it.createdAt }.forEach { if (visible(it)) by[other(it)] = it }
        return by.entries
            .sortedWith(compareByDescending<Map.Entry<String, Message>> { isPinned(it.key) }
                .thenByDescending { it.value.createdAt })
            .map { it.key to it.value }
    }

    fun unread(peer: String): Int {
        val me = meId ?: return 0
        return msgs.count { it.sender == peer && it.receiver == me && it.readAt == null && !it.isDeleted && visible(it) }
    }

    fun lastSeenOf(id: String): String? = seenOverride[id] ?: peers[id]?.lastSeen

    fun openChat(peer: String) { route = Route.Conversation(peer) }

    private fun expFor(peer: String): Long? {
        val s = timers[peer] ?: 0
        return if (s > 0) System.currentTimeMillis() + s * 1000L else null
    }

    private fun rlsMsg(e: Throwable?): String {
        val s = e?.message ?: ""
        return if (s.contains("row-level security", true)) "لا يمكنك مراسلة هذا المستخدم الآن" else "فشل الإرسال: $s"
    }

    // ---------- actions ----------
    fun fetchSeen(id: String) = viewModelScope.launch {
        runCatching { Repo.profile(id) }.getOrNull()?.let { peers[id] = it }
    }

    fun typing(peer: String, on: Boolean) {
        val r = room ?: return
        val me = meId ?: return
        val now = System.currentTimeMillis()
        if (on && now - lastTypingSent < 2000) return
        lastTypingSent = if (on) now else 0
        viewModelScope.launch {
            runCatching {
                r.broadcast("typing", buildJsonObject { put("from", me); put("to", peer); put("on", on) })
            }
        }
    }

    fun markRead(peer: String) {
        val me = meId ?: return
        val un = msgs.filter { it.sender == peer && it.receiver == me && it.readAt == null && it.id !in readSent }
        if (un.isEmpty()) return
        val iso = Instant.now().toString()
        un.forEach { readSent.add(it.id) }
        viewModelScope.launch { runCatching { Repo.markRead(un.map { it.id }, iso) } }
    }

    private fun baseRow(to: String, body: String, replyTo: String?) = buildJsonObject {
        put("sender", meId!!)
        put("receiver", to)
        put("body", body)
        if (replyTo != null) put("reply_to", replyTo)
        expFor(to)?.let { put("expires_at", Instant.ofEpochMilli(it).toString()) }
    }

    private fun rand() = java.lang.Long.toString((Math.random() * 1e12).toLong(), 36)

    fun send(to: String, body: String, replyTo: Message?, img: ByteArray?) {
        val me = meId ?: return
        if (to in blocked) { toast = "هذا المستخدم محظور"; return }
        if (to == me) { toast = "لا يمكنك مراسلة نفسك"; return }
        viewModelScope.launch {
            var path: String? = null
            runCatching {
                if (img != null) {
                    path = "$me/$to/${System.currentTimeMillis()}_${rand()}.jpg"
                    Repo.upload("chat-images", path!!, img, "image/jpeg")
                }
                val row = buildJsonObject {
                    baseRow(to, body.ifEmpty { "📷 صورة" }, replyTo?.id).forEach { (k, v) -> put(k, v) }
                    if (path != null) put("image_path", path!!)
                }
                addMsg(Repo.insertMessage(row))
            }.onFailure {
                path?.let { p -> runCatching { Repo.remove("chat-images", p) } }
                toast = rlsMsg(it)
            }
        }
    }

    private fun canSendTo(to: String): Boolean {
        val me = meId ?: return false
        if (to in blocked) { toast = "هذا المستخدم محظور"; return false }
        if (to == me) { toast = "لا يمكنك مراسلة نفسك"; return false }
        return true
    }

    fun sendFile(to: String, doc: PickedDoc, caption: String, replyTo: Message?) {
        val me = meId ?: return
        if (!canSendTo(to)) return
        viewModelScope.launch {
            val path = "$me/$to/${System.currentTimeMillis()}_${rand()}.${Media.safeExt(doc.name)}"
            toast = "جارٍ رفع الملف..."
            runCatching {
                Repo.upload(Repo.FILES_BUCKET, path, doc.bytes, doc.mime)
                val row = buildJsonObject {
                    baseRow(to, caption.ifEmpty { "📎 ${doc.name}" }, replyTo?.id).forEach { (k, v) -> put(k, v) }
                    put("file_path", path); put("file_name", doc.name); put("file_type", doc.mime)
                }
                addMsg(Repo.insertMessage(row))
            }.onFailure {
                runCatching { Repo.remove(Repo.FILES_BUCKET, path) }
                toast = if (it.message?.contains("not found", true) == true || it.message?.contains("Bucket", true) == true)
                    "bucket المستندات غير موجود — شغّل supabase/features.sql" else rlsMsg(it)
            }
        }
    }

    fun sendLocation(to: String, lat: Double, lng: Double, replyTo: Message?) {
        if (!canSendTo(to)) return
        viewModelScope.launch {
            runCatching {
                addMsg(Repo.insertMessage(baseRow(to, "📍 موقعي\n" + geoUrl(lat, lng), replyTo?.id)))
            }.onFailure { toast = rlsMsg(it) }
        }
    }

    fun sendAudio(to: String, file: File, secs: Int) {
        val me = meId ?: return
        viewModelScope.launch {
            val path = "$me/$to/${System.currentTimeMillis()}_${rand()}.m4a"
            runCatching {
                Repo.upload("chat-audio", path, file.readBytes(), "audio/mp4")
                val row = buildJsonObject {
                    baseRow(to, "🎤 رسالة صوتية", null).forEach { (k, v) -> put(k, v) }
                    put("audio_path", path)
                    put("audio_secs", secs)
                }
                addMsg(Repo.insertMessage(row))
            }.onFailure {
                runCatching { Repo.remove("chat-audio", path) }
                toast = rlsMsg(it)
            }
            file.delete()
        }
    }

    fun editMessage(m: Message, body: String) = viewModelScope.launch {
        runCatching { Repo.editMessage(m.id, body) }
            .onSuccess {
                val i = msgs.indexOfFirst { it.id == m.id }
                if (i >= 0) msgs[i] = msgs[i].copy(body = body, edited = true)
            }
            .onFailure { toast = "فشل التعديل: ${it.message}" }
    }

    fun deleteForAll(m: Message) = viewModelScope.launch {
        runCatching {
            Repo.markDeleted(m.id)
            m.imagePath?.let { Repo.remove("chat-images", it) }
            m.audioPath?.let { Repo.remove("chat-audio", it) }
            m.filePath?.let { Repo.remove(Repo.FILES_BUCKET, it) }
        }.onSuccess {
            val i = msgs.indexOfFirst { it.id == m.id }
            if (i >= 0) msgs[i] = msgs[i].copy(deleted = true)
        }.onFailure { toast = "فشل الحذف: ${it.message}" }
    }

    fun hideLocal(m: Message) {
        hidden = hidden + m.id
        prefs.edit().putStringSet("hidden", hidden).apply()
    }

    fun react(m: Message, emoji: String) {
        val me = meId ?: return
        val mine = reactions.firstOrNull { it.messageId == m.id && it.userId == me }
        viewModelScope.launch {
            runCatching {
                if (mine?.emoji == emoji) {
                    Repo.removeReaction(m.id, me)
                    reactions.remove(mine)
                } else {
                    Repo.setReaction(m.id, me, emoji)
                    reactions.removeAll { it.messageId == m.id && it.userId == me }
                    reactions.add(Reaction(m.id, me, emoji))
                }
            }.onFailure { toast = "تعذّر التفاعل" }
        }
    }

    fun block(id: String) = viewModelScope.launch {
        val me = meId ?: return@launch
        runCatching { Repo.block(me, id) }.onSuccess { blocked = blocked + id }
            .onFailure { toast = "تعذّر الحظر" }
    }

    fun unblock(id: String) = viewModelScope.launch {
        runCatching { Repo.unblock(id) }.onSuccess { blocked = blocked - id }
            .onFailure { toast = "تعذّر إلغاء الحظر" }
    }

    // ---------- users / profile ----------
    suspend fun search(q: String): List<Profile> {
        val me = meId ?: return emptyList()
        val l = runCatching { Repo.searchUsers(q, me) }.getOrDefault(emptyList())
        l.forEach { peers[it.id] = it }
        return l
    }

    fun saveAbout(text: String) = viewModelScope.launch {
        val me = meId ?: return@launch
        runCatching { Repo.setAbout(me, text) }
            .onSuccess { peers[me]?.let { p -> peers[me] = p.copy(about = text) }; toast = "تم الحفظ" }
            .onFailure { toast = "فشل الحفظ: ${it.message}" }
    }

    fun setAvatar(bytes: ByteArray) = viewModelScope.launch {
        val me = meId ?: return@launch
        val old = peers[me]?.avatarPath
        val path = "$me/${System.currentTimeMillis()}.jpg"
        runCatching {
            Repo.upload("avatars", path, bytes, "image/jpeg", upsert = true)
            Repo.setAvatarPath(me, path)
            peers[me]?.let { peers[me] = it.copy(avatarPath = path) }
            old?.let { runCatching { Repo.remove("avatars", it) } }
        }.onSuccess { toast = "تم تحديث الصورة" }.onFailure { toast = "فشل رفع الصورة: ${it.message}" }
    }

    fun removeAvatar() = viewModelScope.launch {
        val me = meId ?: return@launch
        val old = peers[me]?.avatarPath ?: return@launch
        runCatching {
            Repo.setAvatarPath(me, null)
            Repo.remove("avatars", old)
        }.onSuccess { peers[me]?.let { p -> peers[me] = p.copy(avatarPath = null) } }
    }
}
