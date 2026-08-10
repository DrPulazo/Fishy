package com.example.fishy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material3-style radii: small for buttons/fields, medium for cards/accordions.
 * extraLarge matches medium so AlertDialog corners align with AccordionCard
 * (M3 dialogs use shapes.extraLarge by default).
 */
val FishyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(12.dp)
)
