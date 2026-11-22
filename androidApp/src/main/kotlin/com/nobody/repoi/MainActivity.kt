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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

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
