package com.klencod.dardasha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import coil.compose.AsyncImage
import com.klencod.dardasha.data.Media
import com.klencod.dardasha.data.Repo

fun avGradient(name: String): Brush {
    var h = 0
    for (c in name) h = (h * 31 + c.code) % 360
    return Brush.linearGradient(
        listOf(
            Color.hsl(h.toFloat(), 0.55f, 0.42f),
            Color.hsl(((h + 40) % 360).toFloat(), 0.6f, 0.32f)
        )
    )
}

@Composable
fun Avatar(name: String, path: String?, size: Dp = 46.dp, online: Boolean = false) {
    Box(Modifier.size(size)) {
        Box(
            Modifier.size(size).clip(CircleShape).background(avGradient(name)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.take(2).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp
            )
            if (path != null) {
                AsyncImage(
                    model = Repo.avatarUrl(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (online) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(13.dp)
                    .background(Color(0xFF25D366), CircleShape)
                    .border(2.dp, C.panel, CircleShape)
            )
        }
    }
}

/** صورة من bucket خاص (تُنزَّل بالصلاحيات الحالية وتُخزَّن مؤقتاً) */
@Composable
fun StorageImage(
    bucket: String,
    path: String,
    modifier: Modifier = Modifier,
    scale: ContentScale = ContentScale.Crop
) {
    var bmp by remember(path) { mutableStateOf<ImageBitmap?>(Media.cached(path)) }
    LaunchedEffect(path) { if (bmp == null) bmp = Media.image(bucket, path) }
    Box(modifier.background(Color(0x33000000)), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = scale)
    }
}

@Composable
fun SheetItem(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (danger) C.err else C.mute)
        Spacer(Modifier.width(16.dp))
        Text(label, color = if (danger) C.err else C.fg, fontSize = 16.sp)
    }
}

