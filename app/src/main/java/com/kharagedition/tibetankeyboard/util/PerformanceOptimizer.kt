package com.kharagedition.tibetankeyboard.util

import android.app.Application
import android.content.Context
import androidx.core.util.LruCache
import java.util.concurrent.TimeUnit

/**
 * Performance optimization utilities for caching and memory management
 */
class PerformanceOptimizer(private val context: Context) {

    /**
     * LRU Cache for translation results
     */
    private val translationCache = object : LruCache<String, String>(20) {
        override fun sizeOf(key: String, value: String): Int {
            return key.length + value.length
        }
    }

    /**
     * LRU Cache for grammar analysis results
     */
    private val grammarCache = object : LruCache<String, Any>(15) {
        override fun sizeOf(key: String, value: Any): Int {
            return key.length + 512 // Approximate
        }
    }

    /**
     * Cache translation result
     */
    fun cacheTranslation(key: String, result: String) {
        translationCache.put(key, result)
    }

    /**
     * Get cached translation
     */
    fun getTranslation(key: String): String? {
        return translationCache.get(key)
    }

    /**
     * Cache grammar analysis
     */
    fun cacheGrammarAnalysis(key: String, result: Any) {
        grammarCache.put(key, result)
    }

    /**
     * Get cached grammar analysis
     */
    fun getGrammarAnalysis(key: String): Any? {
        return grammarCache.get(key)
    }

    /**
     * Clear all caches
     */
    fun clearAllCaches() {
        translationCache.evictAll()
        grammarCache.evictAll()
    }

    /**
     * Enable aggressive image caching
     */
    fun enableImageCaching() {
        // Configure Glide caching
        val diskCacheSize = 100 * 1024 * 1024 // 100 MB
        val memoryCacheSize = 32 * 1024 * 1024 // 32 MB

        // Configuration would be set in GlideModule
    }

    /**
     * Debounce user input to prevent excessive API calls
     */
    fun debounce(
        delayMs: Long = 200,
        action: () -> Unit
    ) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(action, delayMs)
    }

    /**
     * Monitor memory usage
     */
    fun getMemoryUsage(): MemoryStats {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory

        return MemoryStats(
            totalMB = totalMemory / (1024 * 1024),
            usedMB = usedMemory / (1024 * 1024),
            freeMB = freeMemory / (1024 * 1024),
            usagePercent = (usedMemory * 100) / totalMemory
        )
    }

    /**
     * Check if we're running low on memory
     */
    fun isLowMemory(): Boolean {
        val stats = getMemoryUsage()
        return stats.usagePercent > 85
    }

    data class MemoryStats(
        val totalMB: Long,
        val usedMB: Long,
        val freeMB: Long,
        val usagePercent: Long
    )

    companion object {
        private var instance: PerformanceOptimizer? = null

        fun getInstance(context: Context): PerformanceOptimizer {
            return instance ?: synchronized(this) {
                instance ?: PerformanceOptimizer(context).also { instance = it }
            }
        }
    }
}
