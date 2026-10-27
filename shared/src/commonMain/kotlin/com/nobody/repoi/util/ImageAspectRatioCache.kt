package com.nobody.repoi.util

/**
 * 线程安全且受容量限制的图片纵横比 LRU 缓存器（url -> width / height）。
 * 限制最大 500 项，防止内存无界泄露，供瀑布流组件复用并消除滚动时的高度反复跳变。
 * 采用标准 LinkedHashMap 实现，完全解耦 Android SDK，天然支持 JVM 单元测试。
 */
object ImageAspectRatioCache {
    private const val MAX_ENTRIES = 500

    private val cache = object : LinkedHashMap<String, Float>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    operator fun get(key: String): Float? = cache[key]

    @Synchronized
    operator fun set(key: String, value: Float) {
        cache[key] = value
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }

    @Synchronized
    fun size(): Int = cache.size
}
