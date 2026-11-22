package com.nobody.repoi.ui

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PoiIconsArchitectureTest {

    private val projectRoot = File(".").canonicalFile.let {
        if (File(it, "gradle/libs.versions.toml").exists()) it else it.parentFile
    }

    private val commonMainSrcDir = File(projectRoot, "shared/src/commonMain/kotlin")
    private val drawableDir = File(projectRoot, "shared/src/commonMain/composeResources/drawable")
    private val tomlFile = File(projectRoot, "gradle/libs.versions.toml")
    private val buildGradleFile = File(projectRoot, "shared/build.gradle.kts")

    @Test
    fun noMaterialIconsExtendedImportsInSourceCode() {
        val violations = mutableListOf<String>()

        commonMainSrcDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, rawLine ->
                        val line = rawLine.trim()
                        if (line.startsWith("import androidx.compose.material.icons")) {
                            violations.add("${file.relativeTo(projectRoot).path}:${index + 1} -> $rawLine")
                        }
                    }
                }
            }

        assertTrue(
            violations.isEmpty(),
            "全工程禁止使用 androidx.compose.material.icons，请使用 PoiIcons 或 Res.drawable.ic_ms_*！违规点（共 ${violations.size} 处）：\n" +
                violations.take(20).joinToString("\n") +
                if (violations.size > 20) "\n... 还有 ${violations.size - 20} 处" else ""
        )
    }

    @Test
    fun buildGradleAndTomlDoNotContainMaterialIconsExtended() {
        assertTrue(tomlFile.exists(), "libs.versions.toml 必须存在")
        val tomlContent = tomlFile.readText()
        tomlContent shouldNotContain "material-icons-extended"
        tomlContent shouldNotContain "compose-materialIconsExtended"
        tomlContent shouldNotContain "jetbrains-compose-materialIconsExtended"

        assertTrue(buildGradleFile.exists(), "shared/build.gradle.kts 必须存在")
        val buildGradleContent = buildGradleFile.readText()
        buildGradleContent shouldNotContain "materialIconsExtended"
    }

    @Test
    fun allMaterialSymbolsXmlAssetsAreValid() {
        assertTrue(drawableDir.exists(), "composeResources/drawable 目录必须存在")
        val msFiles = drawableDir.listFiles { file ->
            file.isFile && file.name.startsWith("ic_ms_") && file.name.endsWith(".xml")
        } ?: emptyArray()

        assertTrue(msFiles.isNotEmpty(), "必须包含按需引入的 ic_ms_*_rounded.xml 矢量资产")

        msFiles.forEach { file ->
            val text = file.readText()
            assertTrue(text.isNotBlank(), "${file.name} 内容不得为空")
            text shouldNotContain "?attr/colorControlNormal"
            text shouldNotContain "@android:color/white"
        }
    }
}
