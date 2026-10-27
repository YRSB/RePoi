package com.nobody.repoi.ui.theme

/**
 * 通用多形态设备预览注解，覆盖手机、折叠屏、平板和桌面四种典型视口。
 *
 * KMP 说明：`androidx.compose.ui.tooling.preview` 仅 Android 端完整可用，
 * 此处退化为标记注解以保证 commonMain 全端编译；Android 端预览请另加 `@Preview`。
 */
annotation class FormFactorPreviews
