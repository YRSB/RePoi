package com.nobody.repoi.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ElevatedCard

/**
 * RePoi Material 3 Expressive (M3E) 统一形状系统契约。
 * 对齐官方 M3 / M3E 规范，通过 [MaterialExpressiveTheme] 根节点单向注入全应用：
 * - extraSmall: 4.dp (微型徽章 / 骨架屏占位)
 * - small: 8.dp (小卡片 / 图片 / Chips / 标签)
 * - medium: 12.dp (Card / ElevatedCard 官方基准圆角)
 * - large: 16.dp (M3 Large 容器，Expressive 大卡片)
 * - largeIncreased: 20.dp (M3E 扩展大圆角，顶栏滚动收起等)
 * - extraLarge: 28.dp (Dialog / Modal Bottom Sheet / Side Sheet)
 * - extraLargeIncreased: 32.dp (Floating Toolbar 胶囊高程)
 */
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    largeIncreased = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
)

// === 官方推荐的 Shapes 扩展属性：用于非对称或业务扩展圆角 ===

/** 核心超大业务容器圆角 (24.dp，介于 large 16.dp 与 extraLarge 28.dp 之间) */
val Shapes.largeCard: Shape
    get() = RoundedCornerShape(24.dp)

/** 底部抽屉大圆角 (28.dp 顶部圆角，底部平直贴合) */
val Shapes.bottomSheet: Shape
    get() = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 0.dp, bottomStart = 0.dp)

/** 侧边抽屉大圆角 (28.dp 单侧圆角) */
val Shapes.sideSheet: Shape
    get() = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)

/** 顶部导航栏滚动收起圆角 (20.dp 底部单侧圆角) */
val Shapes.topBarScrolled: Shape
    get() = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)

/** 药丸形状 (Full Shape) */
val Shapes.pill: Shape
    get() = CircleShape

/** 完整圆形 / 药丸形状 (Full Shape) */
val Shapes.full: Shape
    get() = CircleShape