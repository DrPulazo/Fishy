package com.example.fishy

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.fishy.data.settings.FishySettings
import com.example.fishy.ui.navigation.FishyNavHost
import com.example.fishy.ui.theme.FishyTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val fromNotification = intent?.getBooleanExtra("from_notification", false) ?: false
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
                    FishyNavHost(openScheduler = fromNotification)
                }
            }
        }
    }
}
