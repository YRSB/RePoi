package com.nobody.repoi.platform

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppDirsTest {

    @Test
    fun cacheDirPath_returnsValidDirectory() {
        val cacheDir = cacheDirPath()
        cacheDir.shouldNotBeBlank()
        cacheDir shouldContain ".poishelf"
        val file = File(cacheDir)
        assertTrue(file.exists() && file.isDirectory, "Cache 目录应存在且为文件夹: $cacheDir")
    }

    @Test
    fun filesDirPath_returnsValidDirectory() {
        val filesDir = filesDirPath()
        filesDir.shouldNotBeBlank()
        filesDir shouldContain ".poishelf"
        val file = File(filesDir)
        assertTrue(file.exists() && file.isDirectory, "Files 目录应存在且为文件夹: $filesDir")
    }

    @Test
    fun cacheAndFilesDirs_areDistinct() {
        val cacheDir = cacheDirPath()
        val filesDir = filesDirPath()
        assertNotEquals(cacheDir, filesDir, "Cache 与 Files 目录应互不相同")
    }
}
