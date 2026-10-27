package com.nobody.repoi.platform

import android.content.Context
import org.koin.mp.KoinPlatform.getKoin

actual fun appDataDir(): String {
    val context: Context = getKoin().get()
    return context.filesDir.resolve("datastore").apply { mkdirs() }.absolutePath
}
