package com.nobody.repoi.ui.common

import androidx.compose.ui.platform.ClipEntry

expect fun plainTextClipEntry(text: String): ClipEntry

expect fun ClipEntry.plainText(): String?
