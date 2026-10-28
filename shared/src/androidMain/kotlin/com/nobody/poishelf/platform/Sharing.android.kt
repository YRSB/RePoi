package com.nobody.poishelf.platform

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ShareCompat

@Composable
actual fun rememberTextSharing(): TextSharing {
    val context = LocalContext.current
    return remember {
        object : TextSharing {
            override fun share(text: String) {
                val shareIntent = ShareCompat.IntentBuilder(context)
                    .setType("text/plain")
                    .setText(text)
                    .setChooserTitle(text.take(32))
                    .createChooserIntent()
                context.startActivity(shareIntent)
            }
        }
    }
}

actual fun openUrl(url: String): Boolean {
    return try {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(url),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val context = appContextForIntent()
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

private lateinit var appContextHolder: Context

fun initPoiShelfPlatformContext(context: Context) {
    appContextHolder = context.applicationContext
}

private fun appContextForIntent(): Context = appContextHolder

/** 同模块共享的应用上下文（AppInfo/LogShare/AppDirs/ImageModule 共用）。 */
internal fun poiShelfAppContext(): Context = appContextHolder
