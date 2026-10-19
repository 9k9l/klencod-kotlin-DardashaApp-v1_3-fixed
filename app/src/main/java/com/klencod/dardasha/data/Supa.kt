package com.klencod.dardasha.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

object Supa {
    // نفس المشروع المستخدم في نسخة الويب (chat-v46.html)
    const val URL = "https://dmbzwuxnradjlonvyxso.supabase.co"
    const val KEY = "sb_publishable_DsWkTCC1n902lQzj_QXnpg_NgeNw4oF"

    val client: SupabaseClient by lazy {
        createSupabaseClient(URL, KEY) {
            defaultSerializer = KotlinXSerializer(AppJson)
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }
}
