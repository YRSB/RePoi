package com.nobody.repoi.ui.common

import androidx.compose.ui.platform.ClipEntry

/**
 * 纯文本 [ClipEntry] 跨平台构造器（M3 新增 UI 域桥接）。
 *
 * 背景：`ClipEntry` 在 commonMain 没有字符串工厂——android 构造器只接受
 * `android.content.ClipData`，desktop 构造器只接受任意 `nativeClipEntry: Any`
 *（实为 AWT `Transferable`），均无法在 common 内直接构造（已用 javap 对
 * `ui-runtime.jar`/`ui-desktop-1.12.1.jar` 的 `ClipEntry.class` 逐字验证）。
 * 故按 M2 expect/actual 范式收敛为本三文件（common expect + android/jvm actual），
 * 仅供 F4 手办详情 / F10 他人主页等屏的“复制文本”调用点使用。
 *
 * Android label 固定 `"poishelf"`（与原生版 `ClipData.newPlainText("poishelf", …)` 一致）。
 */
expect fun plainTextClipEntry(text: String): ClipEntry
