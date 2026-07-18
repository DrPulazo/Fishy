package com.example.fishy

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.fishy.data.settings.FishySettings
import com.example.fishy.notifications.NotificationScheduler
import com.example.fishy.ui.navigation.FishyNavHost
import com.example.fishy.ui.theme.FishyTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var openScheduler by mutableStateOf(false)
    private var startScheduledId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyNotificationIntent(intent)

        val settingsRepo = FishyApp.instance.settingsRepository

        lifecycleScope.launch {
            settingsRepo.settings
                .map { it.language }
                .distinctUntilChanged()
                .collect { lang -> FishyApp.applyAppLanguage(lang) }
        }

        setContent {
            val settings by settingsRepo.settings.collectAsState(initial = FishySettings())
            FishyTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FishyNavHost(
                        openScheduler = openScheduler,
                        startScheduledId = startScheduledId,
                        onNotificationNavConsumed = {
                            openScheduler = false
                            startScheduledId = null
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyNotificationIntent(intent)
    }

    private fun applyNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(NotificationScheduler.EXTRA_FROM_NOTIFICATION, false) != true &&
            intent?.getBooleanExtra("from_notification", false) != true
        ) {
            return
        }
        val startId = intent.getLongExtra(NotificationScheduler.EXTRA_START_SCHEDULED_ID, -1L)
        if (startId > 0L) {
            startScheduledId = startId
            openScheduler = false
            return
        }
        if (intent.getBooleanExtra(NotificationScheduler.EXTRA_OPEN_SCHEDULER, true)) {
            openScheduler = true
            startScheduledId = null
        }
    }
}
