package com.nobody.repoi.util

import kotlinx.coroutines.sync.Mutex

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
        repeat(50) {
            if (guard.tryLock()) {
                try {
                    cache.clear()
                    return
                } finally {
                    guard.unlock()
                }
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
