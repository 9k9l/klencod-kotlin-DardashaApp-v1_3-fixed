package com.klencod.dardasha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.Route
import com.klencod.dardasha.data.Profile
import kotlinx.coroutines.delay

@Composable
fun NewChatScreen(vm: MainVM) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    LaunchedEffect(q) {
        val v = q.trim()
        if (v.length < 2) { results = emptyList(); searched = false; return@LaunchedEffect }
        delay(300)
        results = vm.search(v); searched = true
    }

    Column(Modifier.fillMaxSize().background(C.panel).safeDrawingPadding()) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.route = Route.Home }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = C.fg)
            }
            Text("محادثة جديدة", color = C.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp), singleLine = true,
            placeholder = { Text("ابحث باسم المستخدم") }, colors = fieldColors(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        )
        Spacer(Modifier.height(8.dp))
        if (searched && results.isEmpty()) {
            Text("لا يوجد مستخدم بهذا الاسم", color = C.mute, modifier = Modifier.padding(24.dp))
        }
        LazyColumn {
            items(results, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.openChat(p.id) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(p.username, p.avatarPath, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(p.username, color = C.fg, fontWeight = FontWeight.Bold)
                        if (!p.about.isNullOrBlank()) Text(p.about, color = C.mute, fontSize = 13.sp, maxLines = 1)
                    }
                }
                HorizontalDivider(color = C.line)
            }
        }
    }
}
