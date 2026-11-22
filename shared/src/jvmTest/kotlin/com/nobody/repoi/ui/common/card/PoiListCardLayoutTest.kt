package com.nobody.repoi.ui.common.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PoiListCardLayoutTest {

    @Test
    fun spaceBetween_alignsContentBottomWithThumbnailBottom() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.fillMaxWidth().testTag("container")) {
                PoiListCard(
                    title = "手办测试标题",
                    onClick = {},
                    verticalArrangement = Arrangement.SpaceBetween,
                    thumbnail = {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("thumbnail")
                        )
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("badge_cluster"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(16.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(16.dp))
                    }
                }
            }
        }

        val thumbBounds = onNodeWithTag("thumbnail", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val clusterBounds = onNodeWithTag("badge_cluster", useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertEquals(
            thumbBounds.bottom.value,
            clusterBounds.bottom.value,
            0.5f,
            "微标签组底部应与缩略图底部严格对齐（缩略图 bottom=${thumbBounds.bottom.value}, 微标签组 bottom=${clusterBounds.bottom.value}）"
        )
    }

    @Test
    fun multiLineTitle_expandsCardHeightNaturallyWithoutClipping() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.fillMaxWidth().testTag("container")) {
                PoiListCard(
                    title = "超长手办多行测试标题\n第二行手办副标题\n第三行手办描述",
                    titleMaxLines = 3,
                    onClick = {},
                    verticalArrangement = Arrangement.SpaceBetween,
                    thumbnail = {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("thumbnail")
                        )
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("badge_cluster"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(20.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(20.dp))
                    }
                }
            }
        }

        val thumbBounds = onNodeWithTag("thumbnail", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val clusterBounds = onNodeWithTag("badge_cluster", useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertTrue(
            clusterBounds.bottom.value >= thumbBounds.bottom.value,
            "多行内容卡片高度应自适应撑开（缩略图 bottom=${thumbBounds.bottom.value}, 微标签组 bottom=${clusterBounds.bottom.value}）"
        )

        assertTrue(
            clusterBounds.top.value > thumbBounds.top.value + 20f,
            "微标签组应处于标题下方，不发生重叠"
        )
    }

    @Test
    fun posterCard_defaultArrangementWithCenterAlignment_preservesVerticalCentering() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.fillMaxWidth().testTag("container")) {
                PoiListCard(
                    onClick = {},
                    verticalAlignment = Alignment.CenterVertically,
                    thumbnail = {
                        Box(
                            modifier = Modifier
                                .size(90.dp, 130.dp)
                                .testTag("poster_thumbnail")
                        )
                    },
                    title = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .testTag("poster_title")
                        )
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .testTag("content_box")
                    )
                }
            }
        }

        val thumbBounds = onNodeWithTag("poster_thumbnail", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val titleBounds = onNodeWithTag("poster_title", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val contentBounds = onNodeWithTag("content_box", useUnmergedTree = true).getUnclippedBoundsInRoot()

        val thumbCenterY = (thumbBounds.top.value + thumbBounds.bottom.value) / 2f
        val groupCenterY = (titleBounds.top.value + contentBounds.bottom.value) / 2f

        assertEquals(
            thumbCenterY,
            groupCenterY,
            0.5f,
            "海报卡垂直居中时右侧内容整体中心应与缩略图垂直中心严格对齐（缩略图中心 $thumbCenterY, 右侧整体中心 $groupCenterY）"
        )
    }
}
