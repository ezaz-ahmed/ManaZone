package dev.ezaz.manazone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import java.security.MessageDigest
import kotlinx.coroutines.launch
import dev.ezaz.manazone.session.SessionManager
import dev.ezaz.manazone.ui.MainViewModel
import dev.ezaz.manazone.ui.ManazoneRoot
import dev.ezaz.manazone.ui.theme.ManazoneTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels { MainViewModel.Factory }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // A session started before the answer has no countdown notification yet.
            if (granted) (application as ManazoneApp).container.showNotificationForActive()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // A restored activity (rotation, process death) must not replay the launch intent.
        if (savedInstanceState == null) handleIntent(intent)
        if (savedInstanceState == null) askForNotificationsOnce()
        setContent {
            ManazoneTheme { ManazoneRoot(vm) }
        }
    }

    /** The countdown notification (with Stop) needs this on Android 13+. Asked once, not on every launch. */
    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val preferences = (application as ManazoneApp).container.preferences
        lifecycleScope.launch {
            if (preferences.notificationPermissionAsked()) return@launch
            preferences.setNotificationPermissionAsked()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != ACTION_START_FOCUS) return
        // Relaunching from Recents re-delivers the original widget intent; don't start again.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        if (minutes !in SessionManager.MIN_MINUTES..SessionManager.MAX_MINUTES) return
        // MainActivity is exported for the launcher, so only intents carrying this install's token may start a session.
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return
        val preferences = (application as ManazoneApp).container.preferences
        lifecycleScope.launch {
            val expected = preferences.startToken()
            if (MessageDigest.isEqual(token.toByteArray(), expected.toByteArray())) vm.startFocus(minutes)
        }
    }

    companion object {
        const val ACTION_START_FOCUS = "dev.ezaz.manazone.action.START_FOCUS"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_TOKEN = "token"
    }
}
