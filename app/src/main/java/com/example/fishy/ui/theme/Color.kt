package com.example.fishy.theme

import androidx.compose.ui.graphics.Color

// Монохромная цветовая схема
val DarkBackground = Color(0xFF1A1A1A)      // Темный фон
val Surface = Color(0xFF2D2D2D)             // Поверхности (вместо CardBackground)
val SurfaceVariant = Color(0xFF3D3D3D)      // Вариант поверхности
val OnSurface = Color(0xFFE0E0E0)           // Текст на поверхности
val OnSurfaceVariant = Color(0xFFB0B0B0)    // Вторичный текст

// Акцентные цвета (только для текста и состояния)
val Success = Color(0xFF4CAF50)            // Зеленый - все хорошо
val Warning = Color(0xFFFF9800)            // Желтый - внимание (перегруз)
val Error = Color(0xFFF44336)              // Красный - ошибка/нехватка