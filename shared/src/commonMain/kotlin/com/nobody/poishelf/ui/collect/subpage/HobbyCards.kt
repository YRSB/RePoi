package com.nobody.poishelf.ui.collect.subpage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nobody.poishelf.data.remote.model.UserCollectHobbyItem
import com.nobody.poishelf.ui.collect.CollectStateOption
import com.nobody.poishelf.ui.common.rememberListImageRequest
import com.nobody.poishelf.ui.item.list.components.HpoiInfoBadgeItem
import com.nobody.poishelf.ui.theme.ExpressiveElevatedCard
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.util.CdnUrlUtils
import com.nobody.poishelf.util.PriceFormatter

// ── 周边卡片字段映射矩阵 ────────────────────────────────────

/**
 * 字段缺失时的占位符。
 *
 * 原版 `y1.c()` 取不到值时返回空串（`HobbyListAdapter` 直接把空串塞进 TextView）。
 * PoiShelf 统一显示「-」，避免同一行里有的位有值有的位空白造成视觉跳动，
 * 同时满足「取不到值不得崩溃」的要求。
 */
private const val HOBBY_FIELD_PLACEHOLDER = "-"

/**
 * 周边卡片可渲染的微标签字段全集。
 *
 * 文案与取值严格对齐原版 `ui/hobby/y1.java`：
 * - 文案表 `y1.b()` → `strings.xml:566-579` 的 `hobby_label_*`
 * - 取值表 `y1.c()` → 逐字段读 `companyName` / `moneyString` / `releaseDate` /
 *   `rating ÷ ratingCount` / `collection.orderMoney` / `collection.orderWay` /
 *   `collection.state` / `collection.orderTime` / `collection.buyNum`
 *
 * @param withPrefixOnGrid 网格卡右上角徽章是否带字段名前缀。
 * 原版 `HobbyGridAdapter.f()` 明确把 `collectState`、`price` 两个字段排除在前缀之外。
 */
internal enum class HobbyCardField(val label: String, val withPrefixOnGrid: Boolean = true) {
    /** 厂商 */
    COMPANY("厂商"),

    /** 定价 */
    PRICE("定价", withPrefixOnGrid = false),

    /** 发售 */
    RELEASE("发售"),

    /** 评分（rating ÷ ratingCount，保留 2 位小数） */
    RATING("评分"),

    /** 途径（本人预定的入手渠道） */
    ORDER_WAY("途径"),

    /** 补款（预定档的订金） */
    ORDER_PRICE("补款"),

    /** 入手（有过档的入手金额） */
    BUY_PRICE("入手"),

    /** 入手（含件数前缀，如「2件300¥」） */
    BUY_PRICE_AND_COUNT("入手"),

    /** 日亚（Amazon 相对定价的溢价百分比） */
    SELLER("日亚"),

    /** 时间（有过档的入手时间） */
    BUY_TIME("时间"),

    /** 状态（收藏状态文案） */
    COLLECT_STATE("状态", withPrefixOnGrid = false);

    /**
     * 取该字段在指定条目上的展示值。
     * 任何分支都不做强制解包，模型字段缺失一律回落「-」。
     */
    fun value(item: UserCollectHobbyItem): String = when (this) {
        COMPANY -> item.companyName.orEmpty().ifBlank { HOBBY_FIELD_PLACEHOLDER }
        PRICE -> item.displayPriceText.ifBlank { HOBBY_FIELD_PLACEHOLDER }
        RELEASE -> item.displayReleaseDateText.ifBlank { HOBBY_FIELD_PLACEHOLDER }
        RATING -> formatRating(item)
        ORDER_WAY -> item.orderWayText ?: HOBBY_FIELD_PLACEHOLDER
        ORDER_PRICE -> item.formattedOrderMoneyText ?: HOBBY_FIELD_PLACEHOLDER
        BUY_PRICE -> item.formattedOrderMoneyText ?: HOBBY_FIELD_PLACEHOLDER
        BUY_PRICE_AND_COUNT -> formatBuyPriceAndCount(item)
        // PoiShelf 的收藏列表响应模型未回传原版 `seller` 对象（Amazon 溢价信息），
        // 该位固定显示占位符，待数据层补齐 `seller` 后即可自动生效。
        SELLER -> HOBBY_FIELD_PLACEHOLDER
        BUY_TIME -> formatBuyTime(item.actualOrderTime)
        COLLECT_STATE -> item.collectStateText
    }
}

/**
 * 评分展示值：原版 `y1.c("rating")` 对 `rating < 0.001` 直接输出 `"0.00"`，
 * 否则按 `rating / ratingCount` 保留 2 位小数。
 */
private fun formatRating(item: UserCollectHobbyItem): String =
    if (item.rating < 0.001) "0.00" else PriceFormatter.formatFixed(item.averageRating, 2)

/**
 * 「入手」字段（原版 `buyPriceAndCount`）：金额 + 件数前缀。
 * 原版逻辑为 `max(buyNum, 1)`，只有件数大于 1 时才拼「N件」。
 */
private fun formatBuyPriceAndCount(item: UserCollectHobbyItem): String {
    val amount = item.formattedOrderMoneyText ?: return HOBBY_FIELD_PLACEHOLDER
    val count = maxOf(item.collection?.buyNum ?: 0, 1)
    return if (count > 1) "${count}件$amount" else amount
}

/**
 * 入手时间展示值，对齐原版 `y1.c("buyTime")` 的 `yyyy/M/d` 格式（不补零）。
 */
private fun formatBuyTime(raw: String?): String {
    val datePart = raw?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
        ?: return HOBBY_FIELD_PLACEHOLDER
    val parts = datePart.split('-', '/')
    if (parts.size < 3) return HOBBY_FIELD_PLACEHOLDER
    val month = parts[1].trimStart('0').ifEmpty { "0" }
    val day = parts[2].substringBefore(' ').trimStart('0').ifEmpty { "0" }
    return "${parts[0]}/$month/$day"
}

/**
 * 列表卡 4 个微标签的取值矩阵，严格照原版 `UserCollectHobbyFragment.java:399-407`：
 * - 预定 + 本人 → 发售 / 定价 / 途径 / 补款
 * - 已入 + 本人 → 入手 / 定价 / 途径 / 日亚
 * - 有过 + 本人 → 入手 / 定价 / 途径 / 时间
 * - 全部档（`collectState` 为空串）→ 厂商 / 定价 / 发售 / 状态
 * - 其余（关注 / 想买 / 有过，以及预定的他人视角）→ 厂商 / 定价 / 发售 / 评分
 */
internal fun hobbyListFields(collectState: String, isSelf: Boolean): List<HobbyCardField> = when {
    isSelf && collectState == CollectStateOption.PREORDER ->
        listOf(HobbyCardField.RELEASE, HobbyCardField.PRICE, HobbyCardField.ORDER_WAY, HobbyCardField.ORDER_PRICE)

    isSelf && collectState == CollectStateOption.BUY ->
        listOf(HobbyCardField.BUY_PRICE_AND_COUNT, HobbyCardField.PRICE, HobbyCardField.ORDER_WAY, HobbyCardField.SELLER)

    isSelf && collectState == CollectStateOption.RESELL ->
        listOf(HobbyCardField.BUY_PRICE, HobbyCardField.PRICE, HobbyCardField.ORDER_WAY, HobbyCardField.BUY_TIME)

    collectState == CollectStateOption.ALL ->
        listOf(HobbyCardField.COMPANY, HobbyCardField.PRICE, HobbyCardField.RELEASE, HobbyCardField.COLLECT_STATE)

    else ->
        listOf(HobbyCardField.COMPANY, HobbyCardField.PRICE, HobbyCardField.RELEASE, HobbyCardField.RATING)
}

/**
 * 网格卡右上角徽章字段，严格照原版 `UserCollectHobbyFragment.java:427-445`：
 * - 预定 → 补款
 * - 已入 → 本人显示入手，他人显示定价
 * - 全部档（`collectState` 为空串）→ 状态
 * - 其余（关注 / 想买 / 有过，以及任何未知档位）→ 评分
 *
 * 原版 `HobbyGridAdapter` 构造期的默认徽章是「点击量(hits)」，但收藏页每次装载后
 * 都会走上面的重算分支，故本函数不产出该兜底值。
 */
internal fun hobbyGridBadgeField(collectState: String, isSelf: Boolean): HobbyCardField = when (collectState) {
    CollectStateOption.PREORDER -> HobbyCardField.ORDER_PRICE
    CollectStateOption.BUY -> if (isSelf) HobbyCardField.BUY_PRICE else HobbyCardField.PRICE
    CollectStateOption.ALL -> HobbyCardField.COLLECT_STATE
    else -> HobbyCardField.RATING
}

// ── 空态 ──────────────────────────────────────────────────

/**
 * 周边页专用空态。
 *
 * 复用了 `EmptyCollectView` 的视觉语言，但额外支持副标题与自定义图标：
 * - 「未收藏周边~」需要引导用户去详情页收藏；
 * - 「用户已隐藏内容」是对端用户主动隐藏的语义（服务端返回「隐藏」字样的错误），
 *   不能沿用「去详情页收藏」的引导。
 */
@Composable
internal fun HobbyEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// ── 卡片 ──────────────────────────────────────────────────

/**
 * 周边网格卡（9:13 海报）。
 *
 * 右上角徽章按收藏状态自适应（见 [hobbyGridBadgeField]），底部叠加发售日期
 * （原版网格卡的 `titleType` 恒为 `release`）。多选态下叠加半透明遮罩与勾选角标。
 *
 * @param isSelectionEnabled 是否允许长按进入多选（对齐原版仅 `isSelf` 时才挂 `onCancelCollect`）。
 * @param selectionMode 当前是否处于多选态，决定是否叠加遮罩与勾选角标。
 * @param isSelected 是否已被加入多选集合（存**外层收藏节点 id**）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HobbyCollectGridCard(
    item: UserCollectHobbyItem,
    badgeField: HobbyCardField,
    isSelectionEnabled: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverUrl = remember(item.actualCover) { CdnUrlUtils.getCoverUrl(item.actualCover) }
    val gridImageRequest = rememberListImageRequest(item.actualCover, coverUrl, "grid")
    val bottomScrim = remember {
        Brush.verticalGradient(colors = listOf(Color.Transparent, Color(0x66000000)))
    }
    val badgeText = badgeField.value(item)
    val badgeContent = remember(badgeField, badgeText) {
        if (badgeField.withPrefixOnGrid || badgeText == HOBBY_FIELD_PLACEHOLDER) {
            badgeText
        } else {
            "${badgeField.label} $badgeText"
        }
    }

    ExpressiveElevatedCard(
        onClick = null,
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick.takeIf { isSelectionEnabled } ?: {}
            )
            .fillMaxWidth()
            .aspectRatio(9f / 13f),
        shape = ExpressiveTokens.SmallCardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            var loadFailed by remember(coverUrl) { mutableStateOf(false) }

            if (coverUrl.isNotBlank() && !loadFailed) {
                AsyncImage(
                    model = gridImageRequest,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = { loadFailed = true }
                )
            } else if (coverUrl.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无配图",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // 右上角状态自适应徽章
            if (badgeContent.isNotBlank()) {
                Surface(
                    shape = ExpressiveTokens.ExtraSmallShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                ) {
                    Text(
                        text = badgeContent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.95f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // 底部发售日期（原版网格卡 titleType 恒为 release）
            val dateText = item.displayReleaseDateText
            if (dateText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(bottomScrim)
                        .padding(top = 16.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.95f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (selectionMode) {
                SelectionOverlay(isSelected = isSelected, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * 周边列表卡（缩略图 + 标题 + 4 个自适应微标签的两行两列）。
 *
 * 4 个微标签的取值矩阵见 [hobbyListFields]；多选态下左侧缩略图叠加勾选角标。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HobbyCollectListCard(
    item: UserCollectHobbyItem,
    fields: List<HobbyCardField>,
    isSelectionEnabled: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverUrl = remember(item.actualCover) { CdnUrlUtils.getCoverSmallUrl(item.actualCover) }
    val thumbImageRequest = rememberListImageRequest(item.actualCover, coverUrl, "thumb")

    ExpressiveElevatedCard(
        onClick = null,
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick.takeIf { isSelectionEnabled } ?: {}
            )
            .fillMaxWidth(),
        shape = ExpressiveTokens.MediumCardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧封面缩略图（68dp 方形，圆角 SmallCardShape）
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(ExpressiveTokens.SmallCardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                AsyncImage(
                    model = thumbImageRequest,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (selectionMode) {
                    SelectionOverlay(
                        isSelected = isSelected,
                        cornerOnly = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 右侧信息列
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 4 个微标签：两行两列等宽，取值随收藏状态自适应
                fields.chunked(2).forEach { rowFields ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowFields.forEach { field ->
                            HpoiInfoBadgeItem(
                                label = field.label,
                                value = field.value(item),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // 奇数个字段时补一个等宽占位，保持两列对齐
                        repeat(2 - rowFields.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 多选态遮罩与勾选角标。
 *
 * @param cornerOnly 列表卡缩略图只需角标、不需要整块压暗时置 true。
 */
@Composable
private fun SelectionOverlay(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    cornerOnly: Boolean = false
) {
    Box(modifier = modifier) {
        if (!cornerOnly) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                        } else {
                            Color.Black.copy(alpha = 0.28f)
                        }
                    )
            )
        }

        Surface(
            shape = CircleShape,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Black.copy(alpha = 0.45f)
            },
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .size(20.dp)
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(3.dp)
                )
            }
        }
    }
}