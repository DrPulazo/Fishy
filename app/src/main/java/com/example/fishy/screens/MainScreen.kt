// MainScreen.kt
package com.example.fishy.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fishy.R
import com.example.fishy.database.AppDatabase
import com.example.fishy.navigation.Screen
import com.example.fishy.theme.FishyTheme
import com.example.fishy.viewmodels.ShipmentViewModel
import com.example.fishy.viewmodels.ShipmentViewModelFactory
import kotlinx.coroutines.delay

@Composable
fun MainScreen(navController: NavController) {
    var showInfoDialog by remember { mutableStateOf(false) }
    var easterEggClickCount by remember { mutableStateOf(0) }
    var showEasterEggDialog by remember { mutableStateOf(false) }
    var lastClickTime by remember { mutableStateOf(0L) }

    // Сброс счетчика через 3 секунды
    LaunchedEffect(easterEggClickCount) {
        if (easterEggClickCount > 0) {
            delay(3000)
            easterEggClickCount = 0
        }
    }

    val context = LocalContext.current
    val viewModel: ShipmentViewModel = viewModel(
        factory = ShipmentViewModelFactory(
            context = context,
            database = AppDatabase.getDatabase(context)
        )
    )

    // ВАЖНО: НЕ ИСПОЛЬЗУЕМ StateFlow для drafts, потому что они не обновляются автоматически
    // Вместо этого используем обычное состояние и обновляем вручную
    var draftsList by remember { mutableStateOf(emptyList<com.example.fishy.utils.DraftData>()) }
    var hasDraft by remember { mutableStateOf(false) }
    var lastDraftId by remember { mutableStateOf<Long?>(null) }

    // Загружаем черновики при каждом появлении экрана
    LaunchedEffect(Unit) {
        draftsList = viewModel.getAllDrafts()
        hasDraft = draftsList.isNotEmpty()
        lastDraftId = if (hasDraft) {
            // Берем последний черновик (самый свежий)
            draftsList.maxByOrNull { it.lastModified }?.id
        } else {
            null
        }
    }

    // Также обновляем при возвращении на этот экран
    LaunchedEffect(navController) {
        // Когда возвращаемся на MainScreen из других экранов
        val backStackEntry = navController.currentBackStackEntry
        backStackEntry?.lifecycle?.addObserver(object : androidx.lifecycle.LifecycleEventObserver {
            override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: androidx.lifecycle.Lifecycle.Event) {
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    // Обновляем список черновиков
                    draftsList = viewModel.getAllDrafts()
                    hasDraft = draftsList.isNotEmpty()
                    lastDraftId = if (hasDraft) {
                        draftsList.maxByOrNull { it.lastModified }?.id
                    } else {
                        null
                    }
                }
            }
        })
    }

    FishyTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Заголовок
                    Text(
                        text = "FISHY",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    // Картинка по центру между заголовком и кнопками
                    Image(
                        painter = painterResource(id = R.drawable.fishylogo),
                        contentDescription = "Логотип",
                        modifier = Modifier
                            .size(350.dp)
                            .clickable {
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastClickTime < 500) {
                                    easterEggClickCount++
                                    if (easterEggClickCount >= 10) {
                                        showEasterEggDialog = true
                                        easterEggClickCount = 0
                                    }
                                }
                                lastClickTime = currentTime
                            }
                            .padding(vertical = 10.dp)
                    )

                    // Кнопки
                    Column(
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Кнопка "Новая отгрузка"
                        OutlinedButton(
                            onClick = {
                                // УБЕДИТЕЛЬНО сбрасываем все данные перед началом новой отгрузки
                                viewModel.startNewShipment()
                                navController.navigate(Screen.NewShipment.route)
                            },
                            modifier = Modifier
                                .width(250.dp)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
                        ) {
                            Text(
                                text = "НОВАЯ ОТГРУЗКА",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Кнопка "Продолжить" (только если есть черновики)
                        if (hasDraft && lastDraftId != null) {
                            OutlinedButton(
                                onClick = {
                                    // ВАЖНО: Используем специальный маршрут для черновиков
                                    navController.navigate(Screen.NewShipmentFromDraft.createRoute(lastDraftId!!))
                                },
                                modifier = Modifier
                                    .width(250.dp)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
                            ) {
                                Text(
                                    text = "ПРОДОЛЖИТЬ",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Кнопка "Расписание"
                        OutlinedButton(
                            onClick = { navController.navigate(Screen.Scheduler.route) },
                            modifier = Modifier
                                .width(250.dp)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
                        ) {
                            Text(
                                text = "РАСПИСАНИЕ",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Кнопка "Архив"
                        OutlinedButton(
                            onClick = { navController.navigate(Screen.Archive.route) },
                            modifier = Modifier
                                .width(250.dp)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
                        ) {
                            Text(
                                text = "АРХИВ",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Кнопка "Черновики"
                        OutlinedButton(
                            onClick = { navController.navigate(Screen.Drafts.route) },
                            modifier = Modifier
                                .width(250.dp)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
                        ) {
                            Text(
                                text = "ЧЕРНОВИКИ",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Кнопка "Шаблоны"
                        OutlinedButton(
                            onClick = { navController.navigate(Screen.Templates.route) },
                            modifier = Modifier
                                .width(250.dp)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
                        ) {
                            Text(
                                text = "ШАБЛОНЫ",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Информационная кнопка в нижнем левом углу
                IconButton(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Информация о приложении",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Диалоговое окно с информацией
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = {
                    Text(
                        text = "О приложении",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Разработано при содействии\nИИ чат-бота DeepSeek.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Text(
                            text = "Бюджет разработки:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        Text(
                            text = "Ящик пива\n12 бессонных ночей",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Text(
                            text = "Обратная связь:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        // Ссылка на Telegram
                        Text(
                            text = "Написать в Telegram",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .clickable {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://t.me/DrPulazo")
                                    )
                                    context.startActivity(intent)
                                }
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )

                        Text(
                            text = "Исходный код:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        // Ссылка на GitHub
                        Text(
                            text = "Открытый код на GitHub",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .clickable {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/DrPulazo/Fishy")
                                    )
                                    context.startActivity(intent)
                                }
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )

                        Text(
                            text = "И помните: самые надежные носители данных — это бумага и карандаш в ваших руках. Приложение — лишь инструмент.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Владивосток\n2025",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { showInfoDialog = false },
                            modifier = Modifier.width(120.dp)
                        ) {
                            Text("Работаем!")
                        }
                    }
                }
            )
        }
    }

    // Диалоговое окно пасхалки
    if (showEasterEggDialog) {
        LaunchedEffect(showEasterEggDialog) {
            // Показываем Toast
            Toast.makeText(
                context,
                "Перекурим, брат?",
                Toast.LENGTH_SHORT
            ).show()

            // Сбрасываем состояние после показа Toast
            showEasterEggDialog = false
        }
    }
}