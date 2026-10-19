package com.klencod.dardasha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.Route
import com.klencod.dardasha.data.fmtList

/** كل الرسائل المميّزة بنجمة، الأحدث أولاً. الضغط يفتح المحادثة، ونجمة اليسار تزيل التمييز. */
@Composable
fun StarredScreen(vm: MainVM) {
    val list = vm.starredMessages()
    Column(Modifier.fillMaxSize().background(C.panel).safeDrawingPadding()) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.route = Route.Home }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = C.fg)
            }
            Text("الرسائل المميّزة بنجمة", color = C.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "لا توجد رسائل مميّزة.\nاضغط مطوّلاً على أي رسالة ثم «تمييز بنجمة».",
                    color = C.mute, modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { m ->
                    val peer = vm.other(m)
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.openChat(peer) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(vm.name(peer), vm.peers[peer]?.avatarPath, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (m.sender == vm.meId) "أنت ← " else "") + vm.name(peer),
                                color = C.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1
                            )
                            Text(vm.preview(m), color = C.fg, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(fmtList(m.createdAt), color = C.mute, fontSize = 11.sp)
                        }
                        IconButton(onClick = { vm.toggleStar(m) }) {
                            Icon(Icons.Filled.Star, "إزالة النجمة", tint = C.star)
                        }
                    }
                    HorizontalDivider(color = C.line)
                }
            }
        }
    }
}
