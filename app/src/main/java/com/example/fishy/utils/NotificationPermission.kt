package com.example.fishy.utils

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun RequestNotificationPermission() {
    val context = LocalContext.current

    // Используем state для управления
    var showPermissionRequest by remember { mutableStateOf(false) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Проверяем текущий статус разрешения
        val permissionStatus = remember {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Разрешение получено
                android.util.Log.d("NotificationPermission", "Permission granted")
            } else {
                // Разрешение отклонено
                android.util.Log.d("NotificationPermission", "Permission denied")
                // Можно показать объяснение или отправить в настройки
            }
        }

        // Проверяем и показываем запрос только один раз
        LaunchedEffect(Unit) {
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                // Ждем немного, чтобы не показывать сразу при запуске
                kotlinx.coroutines.delay(1000)
                showPermissionRequest = true
            }
        }

        // Показываем диалог разрешения
        if (showPermissionRequest) {
            SideEffect {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                showPermissionRequest = false
            }
        }
    }
}