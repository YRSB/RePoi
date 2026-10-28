package com.nobody.poishelf.platform

import android.os.Build

/** 动态取色能力开关（S10）：Android 12（S）及以上支持 Material You。 */
actual val supportsDynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
