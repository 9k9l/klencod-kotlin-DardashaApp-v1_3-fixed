package com.klencod.dardasha.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klencod.dardasha.MainVM
import com.klencod.dardasha.Route
import com.klencod.dardasha.data.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(vm: MainVM) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = vm.meId ?: return
    val prof = vm.peers[me]
    var about by remember(prof?.about) { mutableStateOf(prof?.about ?: "") }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val b = withContext(Dispatchers.IO) { Media.compress(ctx, uri, max = 512, quality = 85) }
            if (b != null) vm.setAvatar(b) else vm.toast = "تعذّر قراءة الصورة"
        }
    }

    Column(Modifier.fillMaxSize().background(C.panel).safeDrawingPadding()) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.route = Route.Home }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = C.fg)
            }
            Text("الملف الشخصي", color = C.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(prof?.username ?: "?", prof?.avatarPath, 120.dp)
            Spacer(Modifier.height(8.dp))
            Row {
                TextButton(onClick = {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("تغيير الصورة", color = C.accent) }
                if (prof?.avatarPath != null) {
                    TextButton(onClick = { vm.removeAvatar() }) { Text("إزالة", color = C.err) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("اسم المستخدم", color = C.mute, fontSize = 13.sp, modifier = Modifier.align(Alignment.Start))
            Text(
                "@" + (prof?.username ?: ""), color = C.fg, fontSize = 17.sp,
                modifier = Modifier.align(Alignment.Start).padding(top = 2.dp, bottom = 16.dp)
            )
            OutlinedTextField(
                about, { about = it.take(140) }, Modifier.fillMaxWidth(),
                label = { Text("حول") }, shape = RoundedCornerShape(12.dp), colors = fieldColors(),
                maxLines = 3
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { vm.saveAbout(about.trim()) },
                enabled = about.trim() != (prof?.about ?: ""),
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.accent, contentColor = C.onAccent)
            ) { Text("حفظ", fontWeight = FontWeight.Bold) }

            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = C.line)
            Row(
                Modifier.fillMaxWidth().clickable { vm.signOut() }.padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = C.err)
                Spacer(Modifier.width(14.dp))
                Text("تسجيل الخروج", color = C.err, fontSize = 16.sp)
            }
        }
    }
}
