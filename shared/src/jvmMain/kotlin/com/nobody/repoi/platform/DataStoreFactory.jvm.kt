package com.nobody.repoi.platform

import java.io.File

actual fun appDataDir(): String {
    val dir = File(System.getProperty("user.home"), ".repoi")
    dir.mkdirs()
    return dir.absolutePath
}
