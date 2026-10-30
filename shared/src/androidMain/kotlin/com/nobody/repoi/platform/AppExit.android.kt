package com.nobody.repoi.platform

import android.app.Activity
import java.lang.ref.WeakReference

/**
 * 退到后台（S9）：`moveTaskToBack(true) || finish()`（原 `PoiShelfApp:160-162` 语义）。
 *
 * Activity 经 [initPoiShelfActivity] 注册（androidApp `MainActivity.onCreate`，thin init）；
 * 未注册时返回 false（M2 无调用方；M6 根组合经此退后台）。
 */
actual fun moveAppToBackground(): Boolean {
    val activity = foregroundActivityRef?.get()
    if (activity == null) return false
    if (activity.moveTaskToBack(true)) return true
    activity.finish()
    return true
}

private var foregroundActivityRef: WeakReference<Activity>? = null

fun initPoiShelfActivity(activity: Activity) {
    foregroundActivityRef = WeakReference(activity)
}

/**
 * 全局未捕获异常钩子：`Thread.setDefaultUncaughtExceptionHandler` 语义，
 * 先执行 [onCrash] 再交还系统默认处理。
 */
actual fun installUncaughtExceptionHook(onCrash: (threadName: String, throwable: Throwable) -> Unit) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching { onCrash(thread.name, throwable) }
        previous?.uncaughtException(thread, throwable)
    }
}
