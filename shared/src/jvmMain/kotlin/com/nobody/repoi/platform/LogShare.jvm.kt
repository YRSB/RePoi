package com.nobody.repoi.platform

import java.io.File

actual suspend fun shareLogFile(fileName: String, text: String) {
    val dir = File(System.getProperty("user.home"), "PoiShelf/logs").apply { mkdirs() }
    runCatching {
        File(dir, fileName).writeText(text)
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(5)
            ?.forEach { runCatching { it.delete() } }
    }
}
