package com.android.prisona.util

import java.lang.Runtime
import com.android.prison.utils.Logger

/**
 * Memory management utility to prevent crashes during memory-intensive operations
 */
object MemoryManager {
    
    private const val TAG = "MemoryManager"
    private const val MEMORY_THRESHOLD = 0.8 // 80% memory usage threshold
    private const val CRITICAL_MEMORY_THRESHOLD = 0.9 // 90% memory usage threshold
    
    /**
     * Check if memory usage is within safe limits
     */
    fun isMemorySafe(): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryUsage = usedMemory.toDouble() / maxMemory.toDouble()
            
            memoryUsage < MEMORY_THRESHOLD
        } catch (e: Exception) {
            Logger.e(TAG, "Error checking memory: ${e.message}")
            true // Assume safe if we can't check
        }
    }
    
    /**
     * Check if memory usage is critical
     */
    fun isMemoryCritical(): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryUsage = usedMemory.toDouble() / maxMemory.toDouble()
            
            memoryUsage > CRITICAL_MEMORY_THRESHOLD
        } catch (e: Exception) {
            Logger.e(TAG, "Error checking critical memory: ${e.message}")
            false // Assume not critical if we can't check
        }
    }
    
    /**
     * Get current memory usage percentage
     */
    fun getMemoryUsagePercentage(): Int {
        return try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryUsage = usedMemory.toDouble() / maxMemory.toDouble()
            
            (memoryUsage * 100).toInt()
        } catch (e: Exception) {
            Logger.e(TAG, "Error getting memory usage: ${e.message}")
            0
        }
    }
    
    /**
     * Force garbage collection if memory usage is high
     */
    fun forceGarbageCollectionIfNeeded(): Boolean {
        return try {
            if (isMemoryCritical()) {
                Logger.w(TAG, "Memory usage critical (${getMemoryUsagePercentage()}%), forcing garbage collection")
                System.gc()
                Thread.sleep(100) // Give GC time to work
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error during garbage collection: ${e.message}")
            false
        }
    }
    
    /**
     * Optimize memory for RecyclerView operations
     */
    fun optimizeMemoryForRecyclerView() {
        try {
            val memoryUsage = getMemoryUsagePercentage()
            
            if (memoryUsage > 70) {
                Logger.d(TAG, "Memory usage high (${memoryUsage}%), optimizing for RecyclerView")
                
                // Force garbage collection
                System.gc()
                
                // Clear any caches if possible
                try {
                    val runtime = Runtime.getRuntime()
                    runtime.gc()
                } catch (e: Exception) {
                    Logger.w(TAG, "Could not force runtime GC: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error optimizing memory: ${e.message}")
        }
    }
    
    /**
     * Check if we should skip icon loading to save memory
     */
    fun shouldSkipIconLoading(): Boolean {
        return try {
            val memoryUsage = getMemoryUsagePercentage()
            memoryUsage > 75 // Skip icon loading if memory usage > 75%
        } catch (e: Exception) {
            Logger.e(TAG, "Error checking if should skip icon loading: ${e.message}")
            false
        }
    }
    
    /**
     * Get memory info for debugging
     */
    fun getMemoryInfo(): String {
        return try {
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            val maxMemory = runtime.maxMemory()
            
            "Memory: ${usedMemory / 1024 / 1024}MB used / ${maxMemory / 1024 / 1024}MB max (${getMemoryUsagePercentage()}%)"
        } catch (e: Exception) {
            "Memory: Unknown (${e.message})"
        }
    }
}
