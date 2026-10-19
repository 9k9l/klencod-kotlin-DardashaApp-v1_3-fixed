package com.klencod.dardasha.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** كل استدعاءات Supabase في مكان واحد (نفس الجداول والـ buckets في نسخة الويب). */
object Repo {
    /** bucket المستندات (أنشئه بملف supabase/features.sql إن لم يكن موجوداً) */
    const val FILES_BUCKET = "chat-files"

    private val sb get() = Supa.client

    // ---------- auth ----------
    suspend fun signIn(email: String, password: String) {
        sb.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    /** يرجع true إذا تم تسجيل الدخول مباشرة، false إذا يلزم تأكيد الإيميل. */
    suspend fun signUp(email: String, password: String, username: String): Boolean {
        sb.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject { put("username", username) }
        }
        return sb.auth.currentSessionOrNull() != null
    }

    suspend fun signOut() = sb.auth.signOut()

    // ---------- messages ----------
    suspend fun loadMessages(): List<Message> =
        sb.from("messages").select {
            order("created_at", Order.DESCENDING)
            limit(1000)
        }.decodeList<Message>()

    suspend fun insertMessage(fields: JsonObject): Message =
        sb.from("messages").insert(fields) { select() }.decodeSingle<Message>()

    suspend fun editMessage(id: String, body: String) {
        sb.from("messages").update({
            set("body", body)
            set("edited", true)
        }) { filter { eq("id", id) } }
    }

    suspend fun markDeleted(id: String) {
        sb.from("messages").update({ set("deleted", true) }) { filter { eq("id", id) } }
    }

    suspend fun markRead(ids: List<String>, iso: String) {
        if (ids.isEmpty()) return
        sb.from("messages").update({ set("read_at", iso) }) { filter { isIn("id", ids) } }
    }

    // ---------- profiles ----------
    suspend fun profiles(ids: Collection<String>): List<Profile> {
        if (ids.isEmpty()) return emptyList()
        return sb.from("profiles").select { filter { isIn("id", ids.toList()) } }.decodeList<Profile>()
    }

    suspend fun profile(id: String): Profile? =
        sb.from("profiles").select { filter { eq("id", id) } }.decodeSingleOrNull<Profile>()

    suspend fun searchUsers(q: String, meId: String): List<Profile> =
        sb.from("profiles").select {
            filter {
                ilike("username", "$q%")
                neq("id", meId)
            }
            limit(15)
        }.decodeList<Profile>()

    suspend fun touchLastSeen(meId: String, iso: String) {
        sb.from("profiles").update({ set("last_seen", iso) }) { filter { eq("id", meId) } }
    }

    suspend fun setAbout(meId: String, about: String) {
        sb.from("profiles").update({ set("about", about) }) { filter { eq("id", meId) } }
    }

    suspend fun setAvatarPath(meId: String, path: String?) {
        sb.from("profiles").update({ set("avatar_path", path) }) { filter { eq("id", meId) } }
    }

    // ---------- features ----------
    suspend fun loadContacts(): List<Contact> = sb.from("contacts").select().decodeList<Contact>()
    suspend fun loadBlocks(): List<BlockRow> = sb.from("blocks").select().decodeList<BlockRow>()
    suspend fun loadReactions(): List<Reaction> = sb.from("message_reactions").select().decodeList<Reaction>()
    suspend fun loadTimers(): List<ChatTimer> = sb.from("chat_timers").select().decodeList<ChatTimer>()

    suspend fun block(meId: String, id: String) {
        sb.from("blocks").insert(buildJsonObject { put("blocker", meId); put("blocked", id) })
    }

    suspend fun unblock(id: String) {
        sb.from("blocks").delete { filter { eq("blocked", id) } }
    }

    suspend fun setReaction(messageId: String, meId: String, emoji: String) {
        val row = buildJsonObject { put("message_id", messageId); put("user_id", meId); put("emoji", emoji) }
        sb.from("message_reactions").upsert(row) {
            onConflict = "message_id,user_id"
        }
    }

    suspend fun removeReaction(messageId: String, meId: String) {
        sb.from("message_reactions").delete {
            filter {
                eq("message_id", messageId)
                eq("user_id", meId)
            }
        }
    }

    // ---------- chat prefs (pin / archive / mute) ----------
    suspend fun loadPrefs(): List<ChatPref> = sb.from("chat_prefs").select().decodeList<ChatPref>()

    suspend fun savePref(meId: String, p: ChatPref) {
        val row = buildJsonObject {
            put("user_id", meId); put("peer", p.peer)
            put("pinned", p.pinned); put("archived", p.archived); put("muted", p.muted)
        }
        sb.from("chat_prefs").upsert(row) { onConflict = "user_id,peer" }
    }

    // ---------- starred messages ----------
    suspend fun loadStars(): List<StarRow> = sb.from("message_stars").select().decodeList<StarRow>()

    suspend fun addStar(meId: String, messageId: String) {
        val row = buildJsonObject { put("user_id", meId); put("message_id", messageId) }
        sb.from("message_stars").upsert(row) { onConflict = "user_id,message_id" }
    }

    suspend fun removeStar(meId: String, messageId: String) {
        sb.from("message_stars").delete {
            filter {
                eq("user_id", meId)
                eq("message_id", messageId)
            }
        }
    }

    // ---------- storage ----------
    suspend fun upload(bucket: String, path: String, bytes: ByteArray, mime: String, upsert: Boolean = false) {
        sb.storage.from(bucket).upload(path, bytes) {
            this.upsert = upsert
            contentType = ContentType.parse(mime)
        }
    }

    suspend fun download(bucket: String, path: String): ByteArray =
        sb.storage.from(bucket).downloadAuthenticated(path)

    suspend fun remove(bucket: String, vararg paths: String) {
        if (paths.isEmpty()) return
        sb.storage.from(bucket).delete(*paths)
    }

    fun avatarUrl(path: String): String = sb.storage.from("avatars").publicUrl(path)
}
