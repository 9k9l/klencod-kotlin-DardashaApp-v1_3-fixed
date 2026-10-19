package com.klencod.dardasha.data

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun parseIso(s: String?): Long? {
    if (s.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
}

private val AR = Locale("ar")
private val timeFmt = DateTimeFormatter.ofPattern("hh:mm a", AR)
private val dateFmt = DateTimeFormatter.ofPattern("d MMM", AR)
private val fullDateFmt = DateTimeFormatter.ofPattern("yyyy/M/d", AR)

fun fmtTime(iso: String?): String {
    val ms = parseIso(iso) ?: return ""
    return timeFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
}

private fun dayDiff(ms: Long): Long {
    val z = ZoneId.systemDefault()
    val d = Instant.ofEpochMilli(ms).atZone(z).toLocalDate()
    return LocalDate.now(z).toEpochDay() - d.toEpochDay()
}

/** وقت مختصر لقائمة المحادثات */
fun fmtList(iso: String?): String {
    val ms = parseIso(iso) ?: return ""
    val diff = dayDiff(ms)
    val z = ZoneId.systemDefault()
    return when {
        diff <= 0 -> fmtTime(iso)
        diff == 1L -> "أمس"
        diff < 7 -> Instant.ofEpochMilli(ms).atZone(z)
            .dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, AR)
        else -> fullDateFmt.format(Instant.ofEpochMilli(ms).atZone(z))
    }
}

/** عنوان فاصل اليوم داخل المحادثة */
fun fmtDay(iso: String?): String {
    val ms = parseIso(iso) ?: return ""
    val diff = dayDiff(ms)
    return when {
        diff <= 0 -> "اليوم"
        diff == 1L -> "أمس"
        else -> dateFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
    }
}

fun fmtSeen(iso: String?): String {
    val ms = parseIso(iso) ?: return ""
    val diff = dayDiff(ms)
    val t = fmtTime(iso)
    return when {
        diff <= 0 -> "اليوم $t"
        diff == 1L -> "أمس $t"
        else -> dateFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())) + " " + t
    }
}

fun dayKey(iso: String?): Long {
    val ms = parseIso(iso) ?: return 0
    return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
}
