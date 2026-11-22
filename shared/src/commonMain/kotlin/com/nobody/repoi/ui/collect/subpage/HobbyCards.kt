package com.nobody.repoi.ui.collect.subpage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.remote.model.UserCollectHobbyItem
import com.nobody.repoi.ui.collect.CollectStateOption
import com.nobody.repoi.ui.common.PoiAsyncImage
import com.nobody.repoi.ui.common.card.PoiListCard
import com.nobody.repoi.ui.common.card.PoiPosterCard
import com.nobody.repoi.ui.common.card.PoiThumbnail
import com.nobody.repoi.ui.item.list.components.HpoiInfoBadgeItem
import com.nobody.repoi.util.PriceFormatter

private const val HOBBY_FIELD_PLACEHOLDER = "-"

internal enum class HobbyCardField(val label: String, val withPrefixOnGrid: Boolean = true) {

    COMPANY("厂商"),

    PRICE("定价", withPrefixOnGrid = false),

    RELEASE("发售"),

    RATING("评分"),

    ORDER_WAY("途径"),

    ORDER_PRICE("补款"),

    BUY_PRICE("入手"),

    BUY_PRICE_AND_COUNT("入手"),

    SELLER("日亚"),

    BUY_TIME("时间"),

    COLLECT_STATE("状态", withPrefixOnGrid = false);

    fun value(item: UserCollectHobbyItem): String = when (this) {
        COMPANY -> item.companyName.orEmpty().ifBlank { HOBBY_FIELD_PLACEHOLDER }
        PRICE -> item.displayPriceText.ifBlank { HOBBY_FIELD_PLACEHOLDER }
        RELEASE -> item.displayReleaseDateText.ifBlank { HOBBY_FIELD_PLACEHOLDER }
        RATING -> formatRating(item)
        ORDER_WAY -> item.orderWayText ?: HOBBY_FIELD_PLACEHOLDER
        ORDER_PRICE -> item.formattedOrderMoneyText ?: HOBBY_FIELD_PLACEHOLDER
        BUY_PRICE -> item.formattedOrderMoneyText ?: HOBBY_FIELD_PLACEHOLDER
        BUY_PRICE_AND_COUNT -> formatBuyPriceAndCount(item)

        SELLER -> HOBBY_FIELD_PLACEHOLDER
        BUY_TIME -> formatBuyTime(item.actualOrderTime)
        COLLECT_STATE -> item.collectStateText
    }
}

private fun formatRating(item: UserCollectHobbyItem): String =
    if (item.rating < 0.001) "0.00" else PriceFormatter.formatFixed(item.averageRating, 2)

private fun formatBuyPriceAndCount(item: UserCollectHobbyItem): String {
    val amount = item.formattedOrderMoneyText ?: return HOBBY_FIELD_PLACEHOLDER
    val count = maxOf(item.collection?.buyNum ?: 0, 1)
    return if (count > 1) "${count}件$amount" else amount
}

private fun formatBuyTime(raw: String?): String {
    val datePart = raw?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
        ?: return HOBBY_FIELD_PLACEHOLDER
    val parts = datePart.split('-', '/')
    if (parts.size < 3) return HOBBY_FIELD_PLACEHOLDER
    val month = parts[1].trimStart('0').ifEmpty { "0" }
    val day = parts[2].substringBefore(' ').trimStart('0').ifEmpty { "0" }
    return "${parts[0]}/$month/$day"
}

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

internal fun hobbyGridBadgeField(collectState: String, isSelf: Boolean): HobbyCardField = when (collectState) {
    CollectStateOption.PREORDER -> HobbyCardField.ORDER_PRICE
    CollectStateOption.BUY -> if (isSelf) HobbyCardField.BUY_PRICE else HobbyCardField.PRICE
    CollectStateOption.ALL -> HobbyCardField.COLLECT_STATE
    else -> HobbyCardField.RATING
}

@Composable
internal fun HobbyEmptyState(
    icon: DrawableResource,
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
            painter = painterResource(icon),
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
    val badgeText = badgeField.value(item)
    val badgeContent = remember(badgeField, badgeText) {
        if (badgeField.withPrefixOnGrid || badgeText == HOBBY_FIELD_PLACEHOLDER) {
            badgeText
        } else {
            "${badgeField.label} $badgeText"
        }
    }

    PoiPosterCard(
        onClick = onClick,
        onLongClick = onLongClick.takeIf { isSelectionEnabled },
        modifier = modifier,
        aspectRatio = 9f / 13f,
        badgeText = badgeContent.takeIf { it.isNotBlank() },
        footerText = item.displayReleaseDateText.takeIf { it.isNotBlank() },
        selectionOverlay = if (selectionMode) {
            { SelectionOverlay(isSelected = isSelected, modifier = Modifier.fillMaxSize()) }
        } else null,
        cover = {
            PoiAsyncImage(
                path = item.actualCover,
                category = MediaCategory.HOBBY,
                purpose = ImagePurpose.CARD_STANDARD,
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}

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
    PoiListCard(
        title = item.displayName,
        onClick = onClick,
        onLongClick = onLongClick.takeIf { isSelectionEnabled },
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
        thumbnail = {
            PoiThumbnail.Square {
                PoiAsyncImage(
                    path = item.actualCover,
                    category = MediaCategory.HOBBY,
                    purpose = ImagePurpose.THUMBNAIL,
                    contentDescription = item.displayName,
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
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

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

                    repeat(2 - rowFields.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

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
                    painter = painterResource(PoiIcons.Check),
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(3.dp)
                )
            }
        }
    }
}
