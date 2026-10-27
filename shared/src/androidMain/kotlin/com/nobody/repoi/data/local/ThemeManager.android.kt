package com.nobody.repoi.data.local

import android.os.Build

actual fun defaultDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
