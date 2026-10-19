package com.klencod.dardasha.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.Route
import com.klencod.dardasha.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: MainVM, peer: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val me = vm.meId ?: return

    val thread by remember(peer) { derivedStateOf { vm.thread(peer) } }
    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    var editing by remember { mutableStateOf<Message?>(null) }
    var menuFor by remember { mutableStateOf<Message?>(null) }
    var viewer by remember { mutableStateOf<String?>(null) }
    var pendingImg by remember { mutableStateOf<ByteArray?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recSecs by remember { mutableIntStateOf(0) }
    var topMenu by remember { mutableStateOf(false) }
    var attachSheet by remember { mutableStateOf(false) }
    var pendingDoc by remember { mutableStateOf<PickedDoc?>(null) }
    var confirmLoc by remember { mutableStateOf(false) }
    var locBusy by remember { mutableStateOf(false) }
    val recorder = remember { VoiceRecorder(ctx) }
    val isBlocked = peer in vm.blocked

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val b = withContext(Dispatchers.IO) { Media.compress(ctx, uri) }
            if (b != null) { pendingImg = b; pendingDoc = null } else vm.toast = "تعذّر قراءة الصورة"
        }
    }
    val docPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Media.readDoc(ctx, uri) } }
            val d = r.getOrNull()
            if (d != null) { pendingDoc = d; pendingImg = null }
            else vm.toast = if (r.exceptionOrNull()?.message == "big") "الملف كبير (الحد الأقصى 20 ميغابايت)" else "تعذّر قراءة الملف"
        }
    }
    fun shareLocation() {
        scope.launch {
            locBusy = true
            val l = Loc.current(ctx)
            locBusy = false
            if (l == null) vm.toast = "تعذّر تحديد موقعك — فعّل خدمة الموقع"
            else vm.sendLocation(peer, l.latitude, l.longitude, replyTo)
            replyTo = null
        }
    }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) shareLocation() else vm.toast = "يلزم إذن الموقع"
    }
    fun startLocation() {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) shareLocation()
        else locPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { if (recorder.start()) recording = true else vm.toast = "تعذّر بدء التسجيل" }
        else vm.toast = "يلزم إذن الميكروفون"
    }

    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    LaunchedEffect(recording) {
        if (recording) { recSecs = 0; while (true) { delay(1000); recSecs++ } }
    }
    LaunchedEffect(peer) { vm.fetchSeen(peer) }
    LaunchedEffect(peer, thread.size) { vm.markRead(peer) }

    fun doSend() {
        val body = text.trim()
        val ed = editing
        if (ed != null) {
            if (body.isNotEmpty() && body != ed.text) vm.editMessage(ed, body)
            editing = null; text = ""; return
        }
        val img = pendingImg
        val doc = pendingDoc
        if (body.isEmpty() && img == null && doc == null) return
        if (doc != null) vm.sendFile(peer, doc, body, replyTo) else vm.send(peer, body, replyTo, img)
        text = ""; replyTo = null; pendingImg = null; pendingDoc = null; vm.typing(peer, false)
    }

    fun startRec() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (recorder.start()) recording = true else vm.toast = "تعذّر بدء التسجيل"
        } else micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(Modifier.fillMaxSize().background(C.bg).safeDrawingPadding()) {
        // ---------- top bar ----------
        Row(
            Modifier.fillMaxWidth().background(C.panel).padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.route = Route.Home }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = C.fg)
            }
            Avatar(vm.name(peer), vm.peers[peer]?.avatarPath, 40.dp, vm.isOnline(peer))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(vm.name(peer), color = C.fg, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                val (sub, live) = when {
                    isBlocked -> "🚫 محظور" to false
                    vm.typingFrom[peer] == true -> "يكتب الآن..." to true
                    vm.isOnline(peer) -> "متصل الآن" to true
                    vm.lastSeenOf(peer) != null -> "آخر ظهور " + fmtSeen(vm.lastSeenOf(peer)) to false
                    else -> "غير متصل" to false
                }
                Text(sub, color = if (live) C.accent else C.mute, fontSize = 12.sp)
            }
            Box {
                IconButton(onClick = { topMenu = true }) { Icon(Icons.Filled.MoreVert, "المزيد", tint = C.mute) }
                DropdownMenu(topMenu, { topMenu = false }) {
                    if (!vm.isArchived(peer)) DropdownMenuItem(
                        text = { Text(if (vm.isPinned(peer)) "إلغاء التثبيت" else "تثبيت المحادثة", color = C.fg) },
                        onClick = { topMenu = false; vm.togglePin(peer) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (vm.isMuted(peer)) "إلغاء الكتم" else "كتم الإشعارات", color = C.fg) },
                        onClick = { topMenu = false; vm.toggleMute(peer) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (vm.isArchived(peer)) "إلغاء الأرشفة" else "أرشفة المحادثة", color = C.fg) },
                        onClick = { topMenu = false; vm.toggleArchive(peer) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBlocked) "إلغاء الحظر" else "حظر", color = if (isBlocked) C.fg else C.err) },
                        onClick = { topMenu = false; if (isBlocked) vm.unblock(peer) else vm.block(peer) }
                    )
                }
            }
        }

        // ---------- messages ----------
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val rev = thread.asReversed()
            items(rev.size, key = { rev[it].id }) { i ->
                val m = rev[i]
                // فاصل اليوم يظهر فوق أول رسالة في اليوم (أي بعدها في القائمة المعكوسة)
                val older = rev.getOrNull(i + 1)
                Column {
                    Bubble(vm, m, m.sender == me, onLong = { menuFor = m }, onImage = { viewer = it })
                    if (older == null || dayKey(older.createdAt) != dayKey(m.createdAt)) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(
                                fmtDay(m.createdAt), color = C.mute, fontSize = 12.sp,
                                modifier = Modifier.background(C.theirs, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // ---------- context bar (reply / edit / image) ----------
        val ctxMsg = editing ?: replyTo
        if (ctxMsg != null || pendingImg != null || pendingDoc != null) {
            Row(
                Modifier.fillMaxWidth().background(C.theirs).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pendingImg?.let { b ->
                    val bmp = remember(b) { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }
                    if (bmp != null) {
                        Image(bmp, null, Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(8.dp))
                    }
                }
                Text(
                    when {
                        editing != null -> "✏ تعديل الرسالة"
                        replyTo != null -> "↩ " + vm.preview(replyTo!!)
                        pendingDoc != null -> "📎 " + pendingDoc!!.name
                        else -> "📷 صورة جاهزة للإرسال"
                    },
                    color = C.mute, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    if (editing != null) text = ""
                    editing = null; replyTo = null; pendingImg = null; pendingDoc = null
                }) { Icon(Icons.Filled.Close, "إلغاء", tint = C.mute) }
            }
        }

        // ---------- input ----------
        when {
            isBlocked -> {
                Box(Modifier.fillMaxWidth().background(C.panel).padding(8.dp), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { vm.unblock(peer) }) { Text("هذا المستخدم محظور — إلغاء الحظر", color = C.accent) }
                }
            }
            recording -> {
                Row(
                    Modifier.fillMaxWidth().background(C.panel).padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { recorder.cancel(); recording = false }) {
                        Icon(Icons.Filled.Delete, "إلغاء", tint = C.mute)
                    }
                    Box(Modifier.size(10.dp).background(C.err, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text("%d:%02d".format(recSecs / 60, recSecs % 60), color = C.fg, modifier = Modifier.weight(1f))
                    FilledIconButton(
                        onClick = {
                            val r = recorder.stop(); recording = false
                            if (r == null) vm.toast = "التسجيل قصير جداً" else vm.sendAudio(peer, r.first, r.second)
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = C.accent, contentColor = C.onAccent)
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "إرسال") }
                }
            }
            else -> {
                Row(
                    Modifier.fillMaxWidth().background(C.panel).padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(
                        Modifier.weight(1f).background(C.theirs, RoundedCornerShape(24.dp)).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it; if (editing == null) vm.typing(peer, it.isNotEmpty()) },
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp),
                            textStyle = TextStyle(color = C.fg, fontSize = 16.sp),
                            cursorBrush = SolidColor(C.accent),
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            decorationBox = { inner ->
                                if (text.isEmpty()) Text("رسالة", color = C.mute, fontSize = 16.sp)
                                inner()
                            }
                        )
                        if (editing == null) {
                            if (locBusy) CircularProgressIndicator(
                                Modifier.padding(12.dp).size(22.dp), color = C.accent, strokeWidth = 2.dp
                            ) else IconButton(onClick = { attachSheet = true }) {
                                Icon(Icons.Filled.AttachFile, "إرفاق", tint = C.mute)
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    val hasContent = text.isNotBlank() || pendingImg != null || pendingDoc != null
                    FilledIconButton(
                        onClick = { if (hasContent) doSend() else startRec() },
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = C.accent, contentColor = C.onAccent)
                    ) {
                        Icon(if (hasContent) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic, if (hasContent) "إرسال" else "تسجيل صوتي")
                    }
                }
            }
        }
    }

    // ---------- message menu ----------
    menuFor?.let { m ->
        val mine = m.sender == me
        val mineReact = vm.reactions.firstOrNull { it.messageId == m.id && it.userId == me }?.emoji
        ModalBottomSheet(onDismissRequest = { menuFor = null }, containerColor = C.panel) {
            Column(Modifier.padding(bottom = 16.dp)) {
                if (!m.isDeleted) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        EMOJIS.forEach { e ->
                            Box(
                                Modifier.size(46.dp).clip(CircleShape)
                                    .background(if (mineReact == e) C.theirs else Color.Transparent)
                                    .clickable { vm.react(m, e); menuFor = null },
                                contentAlignment = Alignment.Center
                            ) { Text(e, fontSize = 24.sp) }
                        }
                    }
                    SheetItem(Icons.AutoMirrored.Filled.Reply, "رد") { replyTo = m; editing = null; menuFor = null }
                    SheetItem(
                        if (vm.isStarred(m.id)) Icons.Filled.StarBorder else Icons.Filled.Star,
                        if (vm.isStarred(m.id)) "إزالة النجمة" else "تمييز بنجمة"
                    ) { vm.toggleStar(m); menuFor = null }
                    if (m.text.isNotBlank() && m.audioPath == null) {
                        SheetItem(Icons.Filled.ContentCopy, "نسخ") {
                            clipboard.setText(AnnotatedString(m.text)); menuFor = null
                        }
                    }
                    if (mine && m.imagePath == null && m.audioPath == null && m.filePath == null) {
                        SheetItem(Icons.Filled.Edit, "تعديل") {
                            editing = m; replyTo = null; text = m.text; menuFor = null
                        }
                    }
                }
                SheetItem(Icons.Filled.Delete, "حذف عندي") { vm.hideLocal(m); menuFor = null }
                if (mine && !m.isDeleted) {
                    SheetItem(Icons.Filled.DeleteForever, "حذف للجميع", danger = true) { vm.deleteForAll(m); menuFor = null }
                }
            }
        }
    }

    // ---------- attach sheet ----------
    if (attachSheet) {
        ModalBottomSheet(onDismissRequest = { attachSheet = false }, containerColor = C.panel) {
            Column(Modifier.padding(bottom = 16.dp)) {
                SheetItem(Icons.Filled.Image, "صورة") {
                    attachSheet = false
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SheetItem(Icons.Filled.Description, "مستند") {
                    attachSheet = false
                    docPick.launch(arrayOf("*/*"))
                }
                SheetItem(Icons.Filled.LocationOn, "الموقع") { attachSheet = false; confirmLoc = true }
            }
        }
    }
    if (confirmLoc) {
        AlertDialog(
            onDismissRequest = { confirmLoc = false },
            containerColor = C.panel,
            title = { Text("مشاركة الموقع", color = C.fg) },
            text = { Text("سيتم إرسال موقعك الحالي إلى ${vm.name(peer)}.", color = C.mute) },
            confirmButton = { TextButton(onClick = { confirmLoc = false; startLocation() }) { Text("إرسال", color = C.accent) } },
            dismissButton = { TextButton(onClick = { confirmLoc = false }) { Text("إلغاء", color = C.mute) } }
        )
    }

    // ---------- full-screen image ----------
    viewer?.let { path ->
        Dialog(onDismissRequest = { viewer = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { viewer = null }) {
                StorageImage("chat-images", path, Modifier.fillMaxSize(), ContentScale.Fit)
                IconButton(onClick = { viewer = null }, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()) {
                    Icon(Icons.Filled.Close, "إغلاق", tint = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(vm: MainVM, m: Message, mine: Boolean, onLong: () -> Unit, onImage: (String) -> Unit) {
    val quoted = m.replyTo?.let { rid -> vm.msgs.firstOrNull { it.id == rid } }
    val rx = vm.reactions.filter { it.messageId == m.id }.groupingBy { it.emoji }.eachCount()

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End) {
        Column(horizontalAlignment = if (mine) Alignment.Start else Alignment.End) {
            Surface(
                color = if (mine) C.mine else C.theirs,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(onClick = {}, onLongClick = onLong)
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    if (m.isDeleted) {
                        Text("🚫 تم حذف هذه الرسالة", color = C.mute, fontStyle = FontStyle.Italic, fontSize = 15.sp)
                    } else {
                        if (quoted != null) {
                            Row(
                                Modifier.padding(bottom = 4.dp).background(Color(0x33000000), RoundedCornerShape(6.dp))
                                    .height(IntrinsicSize.Min)
                            ) {
                                Box(Modifier.width(3.dp).fillMaxHeight().background(C.accent))
                                Column(Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
                                    Text(
                                        if (quoted.sender == vm.meId) "أنت" else vm.name(quoted.sender),
                                        color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold
                                    )
                                    Text(vm.preview(quoted), color = C.mute, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (m.imagePath != null) {
                            if (m.viewOnce == true) {
                                Text(
                                    "① صورة لمرة واحدة\n(افتحها من نسخة الويب)", color = C.mute, fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            } else {
                                StorageImage(
                                    "chat-images", m.imagePath,
                                    Modifier.width(240.dp).heightIn(min = 120.dp, max = 320.dp)
                                        .clip(RoundedCornerShape(8.dp)).clickable { onImage(m.imagePath) }
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        if (m.audioPath != null) {
                            AudioBubble(m.audioPath, (m.audioSecs ?: 0.0).toInt())
                        }
                        if (m.filePath != null) FileBubble(m.filePath, m.fileName, m.fileType)
                        val geo = parseGeo(m.text)
                        if (geo != null) LocationBubble(geo)
                        val showText = geo == null && m.text.isNotBlank() &&
                            m.text != "📷 صورة" && m.text != "🎤 رسالة صوتية" && !(m.viewOnce == true) &&
                            !(m.filePath != null && m.text == "📎 " + (m.fileName ?: ""))
                        if (showText) Text(m.text, color = C.fg, fontSize = 16.sp)
                    }
                    Row(Modifier.align(Alignment.End).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (vm.isStarred(m.id) && !m.isDeleted) {
                            Icon(Icons.Filled.Star, "مميّزة", tint = C.star, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        if (m.isEdited && !m.isDeleted) Text("معدّلة  ", color = C.mute, fontSize = 11.sp)
                        Text(fmtTime(m.createdAt), color = C.mute, fontSize = 11.sp)
                        if (mine && !m.isDeleted) {
                            Spacer(Modifier.width(3.dp))
                            val rd = m.readAt != null
                            Icon(
                                if (rd || vm.isOnline(m.receiver)) Icons.Filled.DoneAll else Icons.Filled.Done, null,
                                tint = if (rd) C.tick else C.mute, modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
            if (rx.isNotEmpty()) {
                Row(
                    Modifier.offset(y = (-4).dp).background(C.panel, RoundedCornerShape(12.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    rx.forEach { (e, n) -> Text(if (n > 1) "$e$n " else "$e ", fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun AudioBubble(path: String, secs: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var pos by remember { mutableIntStateOf(0) }

    DisposableEffect(path) { onDispose { runCatching { player?.release() }; player = null } }
    LaunchedEffect(playing) {
        while (playing) { pos = (player?.currentPosition ?: 0) / 1000; delay(300) }
    }

    Row(Modifier.width(210.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                val p = player
                if (p != null) {
                    if (playing) { p.pause(); playing = false } else { p.start(); playing = true }
                } else if (!loading) scope.launch {
                    loading = true
                    val f = Media.audioFile(ctx, path)
                    loading = false
                    if (f == null) return@launch
                    runCatching {
                        val np = MediaPlayer()
                        np.setDataSource(f.absolutePath)
                        np.prepare()
                        np.setOnCompletionListener { playing = false; pos = 0; it.seekTo(0) }
                        np.start()
                        player = np; playing = true
                    }
                }
            },
            modifier = Modifier.size(40.dp)
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = C.accent, strokeWidth = 2.dp)
            else Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = C.fg)
        }
        LinearProgressIndicator(
            progress = { if (secs > 0) (pos.toFloat() / secs).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.weight(1f), color = C.accent, trackColor = Color(0x33FFFFFF)
        )
        Spacer(Modifier.width(8.dp))
        val shown = if (playing) pos else secs
        Text("%d:%02d".format(shown / 60, shown % 60), color = C.mute, fontSize = 12.sp)
    }
}

@Composable
private fun FileBubble(path: String, name: String?, type: String?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val ext = (name ?: "").substringAfterLast('.', "").uppercase().take(4)
    Row(
        Modifier.widthIn(min = 200.dp)
            .clip(RoundedCornerShape(8.dp)).background(Color(0x33000000))
            .clickable(enabled = !busy) {
                scope.launch {
                    busy = true
                    val ok = Media.openFile(ctx, path, name, type)
                    busy = false
                    if (!ok) android.widget.Toast.makeText(ctx, "تعذّر فتح الملف", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).background(C.accent, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = C.onAccent, strokeWidth = 2.dp)
            else Icon(Icons.Filled.Description, null, tint = C.onAccent)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(name ?: "ملف", color = C.fg, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (ext.isNotEmpty()) Text(ext, color = C.mute, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LocationBubble(g: GeoPoint) {
    val ctx = LocalContext.current
    Row(
        Modifier.widthIn(min = 200.dp)
            .clip(RoundedCornerShape(8.dp)).background(Color(0x33000000))
            .clickable {
                if (!Media.openMap(ctx, g))
                    android.widget.Toast.makeText(ctx, "لا يوجد تطبيق خرائط", android.widget.Toast.LENGTH_SHORT).show()
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).background(C.err, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.LocationOn, null, tint = Color.White) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("📍 موقع", color = C.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("%.5f, %.5f".format(java.util.Locale.US, g.lat, g.lng), color = C.mute, fontSize = 12.sp)
            Text("اضغط للفتح في الخرائط", color = C.accent, fontSize = 12.sp)
        }
    }
}
