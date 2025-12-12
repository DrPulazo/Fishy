package com.example.fishy.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fishy.theme.FishyTheme
import com.example.fishy.theme.LightText
import com.example.fishy.R

@Composable
fun MainScreen(navController: NavController) {
    var showInfoDialog by remember { mutableStateOf(false) }

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
                    // Заголовок (сдвинут выше)
                    Text(
                        text = "FISHY",
                        style = MaterialTheme.typography.displayLarge,
                        color = LightText,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                    )

                    // Картинка по центру между заголовком и кнопками
                    Image(
                        painter = painterResource(id = R.drawable.fishylogo), // Замените your_logo на имя вашего PNG файла
                        contentDescription = "Логотип",
                        modifier = Modifier
                            .size(350.dp) // Размер изображения
                            .padding(vertical = 40.dp) // Отступ сверху и снизу
                    )

                    // Кнопки
                    Column(
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Кнопка "Новая отгрузка"
                        OutlinedButton(
                            onClick = { navController.navigate("new_shipment") },
                            modifier = Modifier
                                .width(250.dp)
                                .height(60.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
                        ) {
                            Text(
                                text = "НОВАЯ ОТГРУЗКА",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Кнопка "Архив"
                        OutlinedButton(
                            onClick = { navController.navigate("archive") },
                            modifier = Modifier
                                .width(250.dp)
                                .height(60.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
                        ) {
                            Text(
                                text = "АРХИВ",
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
                        tint = LightText
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
                            text = "Приложение было создано\nИИ чат-ботом DeepSeek.",
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
                            text = "1 бутылка водки",
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

                        Text(
                            text = "dan5576@mail.ru",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Владивосток",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "2025",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
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
                            Text("ОК")
                        }
                    }
                }
            )
        }
    }
}