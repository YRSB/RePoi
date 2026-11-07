package com.nobody.repoi.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive 设计规范设计令牌（过渡兼容层）。
 *
 * 【架构决策说明 (ADR-0004)】：
 * 绝大多数基础圆角已作为真理来源统一注入根主题 [MaterialExpressiveTheme] 的 [ExpressiveShapes]，
 * 官方标准组件（Card / ElevatedCard / Button / Dialog 等）默认自动消费 [MaterialTheme.shapes]，
 * 业务代码应优先使用 [MaterialTheme.shapes] 原生令牌或 [Shapes] 扩展属性（如 largeCard, bottomSheet），
 * 不再需要从本静态对象手动引用传参。本对象保留以实现向后平滑兼容。
 */
@Deprecated(
    message = "设计系统形状已收敛至 MaterialTheme.shapes 与 Shapes 扩展属性，请优先使用原生主题令牌或通用卡片组件",
    level = DeprecationLevel.WARNING
)
object ExpressiveTokens {
    /** 核心超大容器圆角 (24dp，介于 Large 与 Extra Large 之间的业务大卡片/详情面板) */
    @Deprecated(
        message = "迁移至 MaterialTheme.shapes.largeCard",
        replaceWith = ReplaceWith("MaterialTheme.shapes.largeCard", "androidx.compose.material3.MaterialTheme", "com.nobody.repoi.ui.theme.largeCard")
    )
    val LargeCardShape = RoundedCornerShape(24.dp)

    /** 核心卡片强调大圆角 (M3 Large Container, 16dp，用于强化 Expressive 亲和感) */
    @Deprecated(
        message = "迁移至 MaterialTheme.shapes.large",
        replaceWith = ReplaceWith("MaterialTheme.shapes.large", "androidx.compose.material3.MaterialTheme")
    )
    val CardShape = RoundedCornerShape(16.dp)

    /** 官方通用卡片基准圆角或次级内嵌面板 (M3 Medium Container, 12dp，完全对齐 CardDefaults 基线) */
    @Deprecated(
        message = "迁移至 MaterialTheme.shapes.medium",
        replaceWith = ReplaceWith("MaterialTheme.shapes.medium", "androidx.compose.material3.MaterialTheme")
    )
    val MediumCardShape = RoundedCornerShape(12.dp)

    /** 小卡片/图片圆角 (M3 Small Container, 8dp) */
    @Deprecated(
        message = "迁移至 MaterialTheme.shapes.small",
        replaceWith = ReplaceWith("MaterialTheme.shapes.small", "androidx.compose.material3.MaterialTheme")
    )
    val SmallCardShape = RoundedCornerShape(8.dp)

    /** 标签与筛选 Chip 圆角 (M3 Small Container, 8dp) */
    @Deprecated(
        message = "迁移至 MaterialTheme.shapes.small",
        replaceWith = ReplaceWith("MaterialTheme.shapes.small", "androidx.compose.material3.MaterialTheme")
    )
    val ChipShape = RoundedCornerShape(8.dp)

    /** 微型容器/徽章/占位骨架超小圆角 (M3 Extra Small Container, 4dp) */
    @Deprecated(
        message = "迁移至 MaterialTheme.shapes.extraSmall",
        replaceWith = ReplaceWith("MaterialTheme.shapes.extraSmall", "androidx.compose.material3.MaterialTheme")
    )
    val ExtraSmallShape = RoundedCornerShape(4.dp)

    /** 搜索框/全圆药丸圆角 (Full Shape) */
    @Deprecated(
        message = "迁移至 CircleShape 或 ButtonDefaults.shape",
        replaceWith = ReplaceWith("androidx.compose.foundation.shape.CircleShape")
    )
    val PillShape = CircleShape

    /** 按钮圆角 (Full Shape) */
    @Deprecated(
        message = "迁移至 CircleShape 或 ButtonDefaults.shape",
        replaceWith = ReplaceWith("androidx.compose.foundation.shape.CircleShape")
    )
    val ButtonShape = CircleShape

    /** 对话框大圆角 (M3 Extra Large Container, 28dp) */
    @Deprecated(
        message = "迁移至 MaterialTheme.shapes.extraLarge",
        replaceWith = ReplaceWith("MaterialTheme.shapes.extraLarge", "androidx.compose.material3.MaterialTheme")
    )
    val DialogShape = RoundedCornerShape(28.dp)

    /** 侧边抽屉大圆角 (M3 Modal Side Sheet, 28dp 单侧圆角) */
    @Deprecated(
        message = "迁移至 MaterialTheme.shapes.sideSheet",
        replaceWith = ReplaceWith("MaterialTheme.shapes.sideSheet", "androidx.compose.material3.MaterialTheme", "com.nobody.repoi.ui.theme.sideSheet")
    )
    val SideSheetShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)

    /** 底部抽屉大圆角 (M3 Modal Bottom Sheet, 28dp 顶部圆角，底部平直贴合) */
    @Deprecated(
        message = "迁移至 MaterialTheme.shapes.bottomSheet",
        replaceWith = ReplaceWith("MaterialTheme.shapes.bottomSheet", "androidx.compose.material3.MaterialTheme", "com.nobody.repoi.ui.theme.bottomSheet")
    )
    val BottomSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 0.dp, bottomStart = 0.dp)

    /** 顶部导航栏滚动收起圆角 (M3E Large Increased Container, 20dp 底部单侧圆角) */
    @Deprecated(
        message = "迁移至 MaterialTheme.shapes.topBarScrolled 或 MaterialTheme.shapes.largeIncreased",
        replaceWith = ReplaceWith("MaterialTheme.shapes.topBarScrolled", "androidx.compose.material3.MaterialTheme", "com.nobody.repoi.ui.theme.topBarScrolled")
    )
    val TopBarScrolledShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)

    /** 阴影与标高高度 (Elevations，对齐 M3 Elevation Levels) */
    val ElevationDefault = 1.dp
    val ElevationHovered = 2.dp
    val ElevationPressed = 3.dp
    val ElevationLevel1 = 1.dp
    val ElevationLevel2 = 3.dp
    val ElevationLevel3 = 6.dp
    val ElevationLevel4 = 8.dp
    val DialogElevation = 6.dp
}

/**
 * Material 3 Expressive 标高系统令牌
 */
object ExpressiveElevation {
    val Level0: Dp = 0.dp
    val Level1: Dp = 1.dp
    val Level2: Dp = 3.dp
    val Level3: Dp = 6.dp
    val Level4: Dp = 8.dp
    val Level5: Dp = 12.dp
}

/**
 * M3 Expressive 标准 ElevatedCard 封装组件：
 * 消除 18+ 处重复的 CardDefaults 与 ExpressiveTokens 参数样板代码 (Data Clumps)。
 */
@Composable
fun ExpressiveElevatedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    elevation: Dp = ExpressiveElevation.Level1,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        ElevatedCard(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation),
            content = content
        )
    } else {
        ElevatedCard(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation),
            content = content
        )
    }
}

/**
 * M3 Expressive 标准 Flat/Filled Card 封装组件。
 */
@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            content = content
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            content = content
        )
    }
}

/**
 * M3 Expressive 纯净容器修饰符：
 * 彻底移除 0.5dp 边框，规范使用 M3 5 级 SurfaceContainer 进行背景裁切与着色。
 */
fun Modifier.expressiveSurface(
    shape: Shape = ExpressiveShapes.large,
    containerColor: Color
): Modifier = this
    .clip(shape)
    .background(color = containerColor, shape = shape)

/**
 * 原生 M3 交互点击修饰符（已废弃）：
 * 直接使用原生 Modifier.clickable 即可，保留以便平滑迁移现有调用点。
 */
@Deprecated(
    message = "直接使用 Modifier.clickable，移除中间人与 composed {} 分配开销",
    replaceWith = ReplaceWith("this.clickable(enabled = enabled, onClick = onClick)")
)
fun Modifier.expressiveClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = this.clickable(
    enabled = enabled,
    onClick = onClick
)
