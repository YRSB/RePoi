package com.nobody.repoi.util

import kotlinx.coroutines.sync.Mutex

/**
 * 线程安全且受容量限制的图片纵横比 FIFO/LRU 缓存器（url -> width / height）。
 * 限制最大 500 项，防止内存无界泄露，供瀑布流组件复用并消除滚动时的高度反复跳变。
 * 采用标准 LinkedHashMap 实现，完全解耦 Android SDK，天然支持 JVM 单元测试。
 *
 * 同步机制：以 [Mutex.tryLock] 快速路径保护读写。
 * 竞争失败时返回 null（读操作降级重算）或丢弃（写操作幂等跳过），严禁无锁并发读写破坏内部链表指针。
 */
object ImageAspectRatioCache {
    private const val MAX_ENTRIES = 500

    private val guard = Mutex()
    private val cache = object : LinkedHashMap<String, Float>(MAX_ENTRIES, 0.75f) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    operator fun get(key: String): Float? =
        if (guard.tryLock()) {
            try {
                cache[key]
            } finally {
                guard.unlock()
            }
        } else {
            null
        }

    operator fun set(key: String, value: Float) {
        if (guard.tryLock()) {
            try {
                cache[key] = value
            } finally {
                guard.unlock()
            }
        }
    }

    fun clear() {
        if (guard.tryLock()) {
            try {
                cache.clear()
            } finally {
                guard.unlock()
            }
        }
    }

    fun size(): Int =
        if (guard.tryLock()) {
            try {
                cache.size
            } finally {
                guard.unlock()
            }
        } else {
            0
        }
}
