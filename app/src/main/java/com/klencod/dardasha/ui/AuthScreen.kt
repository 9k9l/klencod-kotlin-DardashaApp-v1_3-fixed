package com.klencod.dardasha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klencod.dardasha.MainVM

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = C.theirs,
    unfocusedContainerColor = C.theirs,
    focusedBorderColor = C.accent,
    unfocusedBorderColor = C.line,
    focusedTextColor = C.fg,
    unfocusedTextColor = C.fg,
    cursorColor = C.accent,
    focusedPlaceholderColor = C.mute,
    unfocusedPlaceholderColor = C.mute,
)

@Composable
fun AuthScreen(vm: MainVM) {
    var signup by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(C.bg).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("دردشة", color = C.accent, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (signup) "أنشئ حساباً جديداً" else "أدخل الإيميل وكلمة المرور للمتابعة.",
            color = C.mute, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        if (signup) {
            OutlinedTextField(
                user, { user = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
                Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
                placeholder = { Text("اسم المستخدم (حروف إنجليزية/أرقام)") }, colors = fieldColors()
            )
            Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(
            email, { email = it.trim() }, Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(12.dp), placeholder = { Text("الإيميل") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), colors = fieldColors()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            pass, { pass = it }, Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(12.dp), placeholder = { Text("كلمة المرور (6 أحرف على الأقل)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), colors = fieldColors()
        )
        Spacer(Modifier.height(8.dp))
        if (err.isNotEmpty()) Text(err, color = C.err, fontSize = 14.sp, textAlign = TextAlign.Center)
        if (info.isNotEmpty()) Text(info, color = C.accent, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                err = ""; info = ""
                if (signup) {
                    if (!Regex("^[A-Za-z0-9_]{3,20}$").matches(user)) {
                        err = "اسم المستخدم: 3 إلى 20 حرف إنجليزي أو رقم أو _"; return@Button
                    }
                    vm.signUp(email, pass, user, { err = it }, {
                        info = "تم التسجيل. أكّد الإيميل من الرسالة ثم ادخل."; signup = false
                    })
                } else vm.signIn(email, pass) { err = it }
            },
            enabled = !vm.authBusy && email.isNotBlank() && pass.length >= 6,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent, contentColor = C.onAccent)
        ) {
            if (vm.authBusy) CircularProgressIndicator(Modifier.size(20.dp), color = C.onAccent, strokeWidth = 2.dp)
            else Text(if (signup) "إنشاء حساب" else "دخول", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = { signup = !signup; err = ""; info = "" }) {
            Text(if (signup) "عندك حساب؟ ادخل" else "ما عندك حساب؟ سجّل", color = C.accent)
        }
    }
}
