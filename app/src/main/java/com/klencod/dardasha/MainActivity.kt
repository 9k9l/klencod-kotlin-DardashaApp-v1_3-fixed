package com.klencod.dardasha

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import com.klencod.dardasha.ui.*

class MainActivity : ComponentActivity() {
    private val vm: MainVM by viewModels()

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            DardashaTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Root(vm)
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val peer = i?.getStringExtra(Notifier.EXTRA_PEER) ?: return
        i.removeExtra(Notifier.EXTRA_PEER)
        vm.openChat(peer)
    }

    override fun onStart() {
        super.onStart()
        AppState.foreground = true
        vm.onForeground()
    }

    override fun onStop() {
        super.onStop()
        AppState.foreground = false
    }
}

@Composable
private fun Root(vm: MainVM) {
    val ctx = LocalContext.current
    LaunchedEffect(vm.toast) {
        vm.toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.toast = null }
    }
    BackHandler(enabled = vm.meId != null && vm.route != Route.Home) { vm.route = Route.Home }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        when {
            vm.booting -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = C.accent)
            vm.meId == null -> AuthScreen(vm)
            else -> when (val r = vm.route) {
                Route.Home -> HomeScreen(vm)
                is Route.Conversation -> ChatScreen(vm, r.peer)
                Route.NewChat -> NewChatScreen(vm)
                Route.Me -> ProfileScreen(vm)
                Route.Archived -> HomeScreen(vm, archivedView = true)
                Route.Starred -> StarredScreen(vm)
            }
        }
    }
}