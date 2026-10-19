package com.klencod.dardasha.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** نفس ألوان نسخة الويب */
object C {
    val bg = Color(0xFF0A1114)
    val panel = Color(0xFF101A20)
    val line = Color(0xFF1C2931)
    val theirs = Color(0xFF1B2830)
    val mine = Color(0xFF0A6B58)
    val accent = Color(0xFF19C3A0)
    val mute = Color(0xFF8696A0)
    val fg = Color(0xFFE9EDEF)
    val err = Color(0xFFF15C6D)
    val tick = Color(0xFF53BDEB)
    val onAccent = Color(0xFF06251F)
    val star = Color(0xFFFFC83D)
}

@Composable
fun DardashaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.accent,
            onPrimary = C.onAccent,
            background = C.bg,
            onBackground = C.fg,
            surface = C.panel,
            onSurface = C.fg,
            surfaceVariant = C.theirs,
            onSurfaceVariant = C.mute,
            error = C.err,
            outline = C.line,
        ),
        content = content
    )
}
