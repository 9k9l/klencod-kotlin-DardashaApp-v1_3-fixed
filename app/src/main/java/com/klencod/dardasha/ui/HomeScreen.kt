package com.klencod.dardasha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.Route
import com.klencod.dardasha.data.fmtList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainVM, archivedView: Boolean = false) {
    var q by remember { mutableStateOf("") }
    var sheetFor by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val searching = q.isNotBlank()
    val all = vm.conversations()
    val archivedCount = all.count { vm.isArchived(it.first) }
    val convs = all.filter {
        // أثناء البحث نبحث في الكل؛ وإلا نعرض المؤرشفة أو العادية حسب الشاشة
        (searching || vm.isArchived(it.first) == archivedView) &&
            (!searching || vm.name(it.first).contains(q.trim(), ignoreCase = true) ||
                vm.preview(it.second).contains(q.trim(), ignoreCase = true))
    }

    Scaffold(
        containerColor = C.panel,
        modifier = Modifier.safeDrawingPadding(),
        floatingActionButton = {
            if (!archivedView) FloatingActionButton(
                onClick = { vm.route = Route.NewChat },
                containerColor = C.accent, contentColor = C.onAccent, shape = CircleShape
            ) { Icon(Icons.Filled.Add, "محادثة جديدة") }
        },
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(C.panel).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (archivedView) {
                    IconButton(onClick = { vm.route = Route.Home }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = C.fg)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(if (archivedView) "المحادثات المؤرشفة" else "دردشة", color = C.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (!archivedView && vm.conn.isNotEmpty()) Text(vm.conn, color = C.mute, fontSize = 12.sp)
                }
                if (!archivedView) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "المزيد", tint = C.mute) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("الرسائل المميّزة بنجمة", color = C.fg) },
                                leadingIcon = { Icon(Icons.Filled.Star, null, tint = C.star) },
                                onClick = { menu = false; vm.route = Route.Starred }
                            )
                        }
                    }
                    IconButton(onClick = { vm.route = Route.Me }) {
                        Icon(Icons.Filled.Person, "حسابي", tint = C.mute)
                    }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                q, { q = it },
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                singleLine = true, shape = RoundedCornerShape(24.dp),
                placeholder = { Text("ابحث عن مستخدم أو محادثة") },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = C.mute) },
                colors = fieldColors()
            )
            if (!archivedView && !searching && archivedCount > 0) {
                Row(
                    Modifier.fillMaxWidth().clickable { vm.route = Route.Archived }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Archive, null, tint = C.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("المؤرشفة", color = C.fg, modifier = Modifier.weight(1f))
                    Text("$archivedCount", color = C.mute, fontSize = 13.sp)
                }
                HorizontalDivider(color = C.line)
            }
            if (convs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (archivedView) "لا توجد محادثات مؤرشفة."
                        else "لا توجد محادثات. اضغط الزر الأخضر لبدء محادثة.",
                        color = C.mute, modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(convs, key = { it.first }) { (peer, last) ->
                        ConversationRow(vm, peer, last, onLong = { sheetFor = peer })
                        HorizontalDivider(color = C.line)
                    }
                }
            }
        }
    }

    sheetFor?.let { peer ->
        ModalBottomSheet(onDismissRequest = { sheetFor = null }, containerColor = C.panel) {
            Column(Modifier.padding(bottom = 16.dp)) {
                Text(
                    vm.name(peer), color = C.fg, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                if (!vm.isArchived(peer)) {
                    SheetItem(Icons.Filled.PushPin, if (vm.isPinned(peer)) "إلغاء التثبيت" else "تثبيت المحادثة") {
                        vm.togglePin(peer); sheetFor = null
                    }
                }
                SheetItem(
                    if (vm.isMuted(peer)) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                    if (vm.isMuted(peer)) "إلغاء الكتم" else "كتم الإشعارات"
                ) { vm.toggleMute(peer); sheetFor = null }
                SheetItem(
                    if (vm.isArchived(peer)) Icons.Filled.Unarchive else Icons.Filled.Archive,
                    if (vm.isArchived(peer)) "إلغاء الأرشفة" else "أرشفة المحادثة"
                ) { vm.toggleArchive(peer); sheetFor = null }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(vm: MainVM, peer: String, last: com.klencod.dardasha.data.Message, onLong: () -> Unit) {
    val name = vm.name(peer)
    val un = vm.unread(peer)
    val typing = vm.typingFrom[peer] == true
    val mine = last.sender == vm.meId
    val muted = vm.isMuted(peer)
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = { vm.openChat(peer) }, onLongClick = onLong)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name, vm.peers[peer]?.avatarPath, 50.dp, vm.isOnline(peer))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name, color = C.fg, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                if (muted) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.NotificationsOff, "مكتومة", tint = C.mute, modifier = Modifier.size(15.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (typing) {
                    Text("يكتب...", color = C.accent, fontSize = 14.sp)
                } else {
                    if (mine && !last.isDeleted) {
                        val rd = last.readAt != null
                        Icon(
                            if (rd || vm.isOnline(peer)) Icons.Filled.DoneAll else Icons.Filled.Done, null,
                            tint = if (rd) C.tick else C.mute, modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        vm.preview(last), color = C.mute, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(fmtList(last.createdAt), color = if (un > 0 && !muted) C.accent else C.mute, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.isPinned(peer)) {
                    Icon(Icons.Filled.PushPin, "مثبّتة", tint = C.mute, modifier = Modifier.size(16.dp))
                    if (un > 0) Spacer(Modifier.width(4.dp))
                }
                if (un > 0) {
                    Box(
                        Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                            .background(if (muted) C.mute else C.accent, CircleShape).padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("$un", color = C.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                } else if (!vm.isPinned(peer)) Spacer(Modifier.height(20.dp))
            }
        }
    }
}
