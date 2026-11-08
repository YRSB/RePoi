package com.nobody.repoi.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

/**
 * 桌面端「滚轮缩放 + 左键拖拽平移」平台基元（`expect` 声明）。
 *
 * 触控设备（Android）无需此扩展：捏合、单指平移、双击、单击全部由 `commonMain`
 * 的 `transformable` / `detectDragGestures` / `combinedClickable` 覆盖，
 * 故 [androidMain] 实现为空 `Modifier`。JVM（Desktop）侧以指针事件补充鼠标语义，
 * 避免 `commonMain` 出现 `java.awt` / `PointerEventType.Scroll` 之外的宿主专属 API 泄漏。
 *
 * 语义约定：
 * - [onZoom] 只报告**相对倍率**与**指针锚点**（当前指针在组件内的局部坐标），
 *   由调用方决定如何换算成缩放值与偏移，保证桌面与触控共用同一套边界钳制逻辑；
 * - [onPan] 报告已被宿主识别为「拖拽」后的位移增量，调用方按需钳制；
 * - [isZoomed] 让宿主决定是否在按下后进入拖拽平移（未放大时把左键交给点击手势）。
 *
 * @param enabled 是否启用桌面指针增强（恒为 `true` 时等价于触控行为不变）
 * @param onZoom 滚轮缩放回调：相对倍率 + 指针锚点
 * @param onPan 放大态左键拖拽平移回调：位移增量
 * @param isZoomed 当前是否处于放大态（决定左键拖拽是否平移）
 */
@Composable
expect fun Modifier.poiDesktopZoomAndPan(
    enabled: Boolean,
    onZoom: (factor: Float, anchor: Offset) -> Unit,
    onPan: (delta: Offset) -> Unit,
    isZoomed: () -> Boolean
): Modifier
