package dev.ezaz.manazone

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.ezaz.manazone.session.SessionManager
import dev.ezaz.manazone.ui.MainViewModel
import dev.ezaz.manazone.ui.ManazoneRoot
import dev.ezaz.manazone.ui.theme.ManazoneTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // A restored activity (rotation, process death) must not replay the launch intent.
        if (savedInstanceState == null) handleIntent(intent)
        // The countdown notification (with Stop) needs this on Android 13+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && savedInstanceState == null) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            ManazoneTheme { ManazoneRoot(vm) }
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
        if (minutes in SessionManager.MIN_MINUTES..SessionManager.MAX_MINUTES) vm.startFocus(minutes)
    }

    companion object {
        const val ACTION_START_FOCUS = "dev.ezaz.manazone.action.START_FOCUS"
        const val EXTRA_MINUTES = "minutes"
    }
}
