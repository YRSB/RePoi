package com.nobody.repoi.platform

/**
 * 导航键实例唯一标识（M6）。
 *
 * 原生 `NavRoutes.kt` 各 `*NavKey` 以 `java.util.UUID.randomUUID().toString()` 作
 * 防身份碰撞 id；`java.util.UUID` 在 commonMain 不可用，按 M2 expect/actual
 * 范式收敛为本三文件。Android/jVM 实际均返回随机 UUID 字符串。
 */
expect fun newNavId(): String
