package com.nobody.repoi.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ElevatedCard

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    largeIncreased = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
)

val Shapes.largeCard: Shape
    get() = RoundedCornerShape(24.dp)

val Shapes.bottomSheet: Shape
    get() = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 0.dp, bottomStart = 0.dp)

val Shapes.sideSheet: Shape
    get() = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)

val Shapes.topBarScrolled: Shape
    get() = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)

val Shapes.pill: Shape
    get() = CircleShape

val Shapes.full: Shape
    get() = CircleShape
