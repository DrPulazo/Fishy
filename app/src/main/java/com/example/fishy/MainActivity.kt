// MainActivity.kt
package com.example.fishy

import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.fishy.navigation.AppNavigation
import com.example.fishy.theme.FishyTheme
import com.example.fishy.utils.NotificationHelper
import com.example.fishy.viewmodels.SchedulerViewModel
import com.example.fishy.viewmodels.SchedulerViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Обработка кликов по уведомлениям
        val fromNotification = intent?.getBooleanExtra("from_notification", false) ?: false
        val shipmentId = intent?.getLongExtra("shipment_id", 0L) ?: 0L

        setContent {
            FishyTheme {
                // Инициализация уведомлений
                LaunchedEffect(Unit) {
                    if (fromNotification && shipmentId > 0) {
                        // Можно открыть сразу диалог чек-листа для этой отгрузки
                        // Или перейти на экран планировщика
                        println("Открыто из уведомления для отгрузки $shipmentId")
                    }
                }

                NotificationInitializer()
                AppNavigation(fromNotification = fromNotification)
            }
        }
    }
}

@Composable
fun NotificationInitializer() {
    val context = LocalContext.current

    // Безопасное получение Application
    val application = context.applicationContext as? android.app.Application
        ?: return  // Если не удалось привести, просто выходим

    val factory = remember {
        SchedulerViewModelFactory(application)
    }

    val viewModel: SchedulerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)

    // При старте приложения перепланируем все уведомления
    LaunchedEffect(Unit) {
        try {
            viewModel.rescheduleAllNotifications()
        } catch (e: Exception) {
            Log.e("NotificationInitializer", "Ошибка при инициализации уведомлений", e)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    FishyTheme {
        AppNavigation(fromNotification = false)
    }
}

class FishyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Перепланируем все уведомления при запуске приложения
        CoroutineScope(Dispatchers.IO).launch {
            NotificationHelper(this@FishyApplication).rescheduleAllNotifications()
        }
    }
}