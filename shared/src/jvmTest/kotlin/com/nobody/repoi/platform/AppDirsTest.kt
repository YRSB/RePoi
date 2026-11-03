package com.nobody.repoi.platform

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * JVM / 桌面端应用目录解析测试（AppDirs.jvm）。
 *
 * 验证桌面环境下的用户主目录 .poishelf/cache 与 .poishelf/files 路径解析、
 * 目录自动创建以及两者的空间隔离性。
 */
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
