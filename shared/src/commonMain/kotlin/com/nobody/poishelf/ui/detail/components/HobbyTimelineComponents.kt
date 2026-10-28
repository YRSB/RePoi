package com.nobody.poishelf.ui.detail.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobody.poishelf.data.remote.model.HobbyTimelineItem
import com.nobody.poishelf.ui.theme.ExpressiveTokens

/**
 * 手办详情页单流中的发售情报时间轴内联折叠卡片。
 * 遵循单流规范，免弹窗打断：
 * - 情报条数 == 1 时：直接平铺为单条情报卡片，隐藏点线导轨与圆点，保持界面平整；
 * - 情报条数 == 2 时：展示完整的双节点时间轴树（首尾导轨连线）；
 * - 情报条数 > 2 时：默认折叠仅展示最新 1 条摘要并提供「展开全部 (N) ▼」，点击后通过 animateContentSize() 平滑展开完整时间轴树。
 */
@Composable
fun HobbyTimelineSection(
    timeline: List<HobbyTimelineItem>,
    modifier: Modifier = Modifier
) {
    if (timeline.isEmpty()) return

    // 当情报数大于 2 时支持展开/收起折叠态
    val canExpand = timeline.size > 2
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 标题行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "情报时间轴",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (canExpand) {
                Row(
                    modifier = Modifier
                        .clip(ExpressiveTokens.PillShape)
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (isExpanded) "收起 ▲" else "展开全部 ▼",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 核心时间轴主卡片（自带平滑高度过渡动画）
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            shape = ExpressiveTokens.LargeCardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (timeline.size == 1 || (canExpand && !isExpanded)) {
                    // 单条记录（无连线上下文）或折叠态：直接平铺为整洁的无导轨卡片
                    TimelineCollapsedItem(item = timeline.first())
                } else {
                    // 多条记录且展开（或条数刚好为 2）：展示包含左侧点线导轨的时间轴树
                    timeline.forEachIndexed { index, item ->
                        TimelineNodeItem(
                            item = item,
                            isFirst = index == 0,
                            isLast = index == timeline.lastIndex
                        )
                    }
                }
            }
        }
    }
}

/**
 * 折叠状态下的单条情报卡片内部摘要。
 */
@Composable
private fun TimelineCollapsedItem(item: HobbyTimelineItem) {
    TimelineItemContent(item = item)
}

/**
 * 时间轴事件项的核心文本内容（事件类型 Chip + 发生日期 + 备注说明）。
 */
@Composable
private fun TimelineItemContent(
    item: HobbyTimelineItem,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = ExpressiveTokens.ChipShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = item.displayTypeLabel,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (!item.actionDateString.isNullOrBlank()) {
                Text(
                    text = item.actionDateString,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!item.actionComment.isNullOrBlank()) {
            Text(
                text = item.actionComment,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 竖向时间轴单个节点项（左侧导轨连线 + 圆点 + 右侧内容卡片）。
 */
@Composable
private fun TimelineNodeItem(
    item: HobbyTimelineItem,
    isFirst: Boolean,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // 左侧导轨：上连线 + 圆点 + 下连线
        Column(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 上半部连线（首个节点隐藏）
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(8.dp)
                    .background(
                        if (isFirst) Color.Transparent
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )

            // 圆点：首个最新节点强化双层光环，历史节点为中性实心点
            if (isFirst) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(MaterialTheme.colorScheme.primary, ExpressiveTokens.PillShape)
                        .padding(2.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, ExpressiveTokens.PillShape)
                        .padding(2.dp)
                        .background(MaterialTheme.colorScheme.primary, ExpressiveTokens.PillShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.outline, ExpressiveTokens.PillShape)
                )
            }

            // 下半部连线（末尾节点隐藏）
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(
                        if (isLast) Color.Transparent
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // 右侧内容卡片
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 10.dp),
            shape = ExpressiveTokens.MediumCardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            TimelineItemContent(
                item = item,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}
