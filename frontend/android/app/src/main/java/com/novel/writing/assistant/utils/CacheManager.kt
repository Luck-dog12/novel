package com.novel.writing.assistant.utils

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class CacheManager {
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val defaultExpiryTime = TimeUnit.MINUTES.toMillis(5)
    
    data class CacheEntry(
        val data: Any?,
        val timestamp: Long,
        val expiryTime: Long
    )
    
    /**
     * 缓存数据
     * @param key 缓存键
     * @param data 缓存数据
     * @param expiryTime 过期时间（毫秒）
     */
    fun <T> put(key: String, data: T, expiryTime: Long = defaultExpiryTime) {
        cache[key] = CacheEntry(
            data = data,
            timestamp = System.currentTimeMillis(),
            expiryTime = expiryTime
        )
    }
    
    /**
     * 获取缓存数据
     * @param key 缓存键
     * @return 缓存数据，如果不存在或已过期则返回null
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        val entry = cache[key]
        if (entry == null) return null
        
        val now = System.currentTimeMillis()
        if (now - entry.timestamp > entry.expiryTime) {
            cache.remove(key)
            return null
        }
        
        return entry.data as T
    }
    
    /**
     * 检查缓存是否存在
     * @param key 缓存键
     * @return 如果缓存存在且未过期则返回true
     */
    fun contains(key: String): Boolean {
        val entry = cache[key]
        if (entry == null) return false
        
        val now = System.currentTimeMillis()
        if (now - entry.timestamp > entry.expiryTime) {
            cache.remove(key)
            return false
        }
        
        return true
    }
    
    /**
     * 移除缓存
     * @param key 缓存键
     */
    fun remove(key: String) {
        cache.remove(key)
    }
    
    /**
     * 清空缓存
     */
    fun clear() {
        cache.clear()
    }
    
    /**
     * 获取缓存大小
     */
    fun size(): Int {
        // 清理过期缓存
        val now = System.currentTimeMillis()
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.timestamp > entry.value.expiryTime) {
                iterator.remove()
            }
        }
        
        return cache.size
    }
    
    companion object {
        // 单例模式
        val instance by lazy { CacheManager() }
    }
}
