package com.example.fishy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Unified corner radius for cards, buttons, text fields, etc. */
val FishyCornerRadius = 5.dp

val FishyShapes = Shapes(
    extraSmall = RoundedCornerShape(FishyCornerRadius),
    small = RoundedCornerShape(FishyCornerRadius),
    medium = RoundedCornerShape(FishyCornerRadius),
    large = RoundedCornerShape(FishyCornerRadius),
    extraLarge = RoundedCornerShape(FishyCornerRadius)
)
