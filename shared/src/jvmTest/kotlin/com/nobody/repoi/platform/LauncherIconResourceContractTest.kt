package com.nobody.repoi.platform

import io.kotest.matchers.string.shouldContain
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class LauncherIconResourceContractTest {

    private val resDir = File("../androidApp/src/main/res").let {
        if (it.exists()) it else File("androidApp/src/main/res")
    }

    @Test
    fun valuesColors_definesLegacyFallbackColors() {
        val colorsFile = File(resDir, "values/colors.xml")
        assertTrue(colorsFile.exists(), "values/colors.xml 必须存在")
        val content = colorsFile.readText()
        content shouldContain """<color name="ic_launcher_background">#DEEDFF</color>"""
        content shouldContain """<color name="ic_launcher_foreground">#004D79</color>"""
    }

    @Test
    fun valuesV31Colors_definesMonetDynamicColors() {
        val colorsFile = File(resDir, "values-v31/colors.xml")
        assertTrue(colorsFile.exists(), "values-v31/colors.xml 必须存在")
        val content = colorsFile.readText()
        content shouldContain """<color name="ic_launcher_background">@android:color/system_accent1_100</color>"""
        content shouldContain """<color name="ic_launcher_foreground">@android:color/system_accent1_700</color>"""
    }

    @Test
    fun valuesNightV31Colors_definesMonetDarkDynamicColors() {
        val colorsFile = File(resDir, "values-night-v31/colors.xml")
        assertTrue(colorsFile.exists(), "values-night-v31/colors.xml 必须存在")
        val content = colorsFile.readText()
        content shouldContain """<color name="ic_launcher_background">@android:color/system_accent1_800</color>"""
        content shouldContain """<color name="ic_launcher_foreground">@android:color/system_accent1_100</color>"""
    }

    @Test
    fun mipmapAnydpiV31_declaresDynamicAdaptiveIcons() {
        listOf("ic_launcher.xml", "ic_launcher_round.xml").forEach { fileName ->
            val iconFile = File(resDir, "mipmap-anydpi-v31/$fileName")
            assertTrue(iconFile.exists(), "mipmap-anydpi-v31/$fileName 必须存在")
            val content = iconFile.readText()
            content shouldContain """<background android:drawable="@color/ic_launcher_background""""
            content shouldContain """<foreground android:drawable="@drawable/ic_launcher_foreground_dynamic""""
            content shouldContain """<monochrome android:drawable="@drawable/ic_launcher_monochrome""""
        }
    }

    @Test
    fun monochromeVector_usesPureWhiteFill() {
        val monoFile = File(resDir, "drawable/ic_launcher_monochrome.xml")
        assertTrue(monoFile.exists(), "drawable/ic_launcher_monochrome.xml 必须存在")
        val content = monoFile.readText()
        content shouldContain """android:fillColor="#FFFFFF""""
    }

    @Test
    fun dynamicForegroundVector_usesDynamicForegroundColor() {
        val fgFile = File(resDir, "drawable/ic_launcher_foreground_dynamic.xml")
        assertTrue(fgFile.exists(), "drawable/ic_launcher_foreground_dynamic.xml 必须存在")
        val content = fgFile.readText()
        content shouldContain """android:fillColor="@color/ic_launcher_foreground""""
    }
}
