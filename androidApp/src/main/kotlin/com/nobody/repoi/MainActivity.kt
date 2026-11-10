package com.nobody.repoi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.nobody.repoi.data.local.ThemeManager
import com.nobody.repoi.ui.navigation.RePoiApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * Android 宿主 Activity（薄壳：只挂生命周期与启动遮罩，业务全部在 [RePoiApp]）。
 *
 * 冷启动时先用系统启动遮罩（`Theme.RePoi.Starting`）遮住 DataStore 读盘窗口，
 * 直到 [ThemeManager.awaitSettings] 返回持久化主题配置才淡出（ADR-0012）。
 * 这样首帧展开即为用户选定的配色，不会先闪一版默认动态取色；
 * 桌面端没有原生遮罩，由 `ThemeMotion` 的首态落定门禁兜底。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate 之前安装，否则拿不到本次启动的 SplashScreen 实例
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 待首次读盘落定后再放行遮罩：生命周期协程在 Activity 销毁时自动取消，
        // 不会把遮罩条件悬在已停止的实例上。
        // Koin 已在 Application.onCreate 完成装配；取不到实例时宁可立即放行，也不要永久遮挡启动。
        val themeManager = KoinPlatform.getKoinOrNull()?.getOrNull<ThemeManager>()
        if (themeManager != null) {
            val themeReady = MutableStateFlow(false)
            splashScreen.setKeepOnScreenCondition { !themeReady.value }
            lifecycleScope.launch {
                themeManager.awaitSettings()
                themeReady.value = true
            }
        }

        setContent {
            RePoiApp()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    RePoiApp()
}
