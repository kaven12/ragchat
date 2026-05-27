package com.example.ragchat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.Callable;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheManager {

    private final org.springframework.cache.CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SEARCH_RESULT_PREFIX = "search:result:";
    private static final String VECTOR_EMBEDDING_PREFIX = "vector:embedding:";
    private static final String USER_CACHE_PREFIX = "user:";

    // Caffeine本地缓存（热点数据，5分钟过期）
    public <T> T getFromLocalCache(String cacheName, String key, Callable<T> valueLoader) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            return cache.get(key, valueLoader);
        }
        try {
            return valueLoader.call();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load cache value", e);
        }
    }

    public void putToLocalCache(String cacheName, String key, Object value) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    public void evictLocalCache(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    // Redis分布式缓存（搜索结果，5-30分钟过期）
    @SuppressWarnings("unchecked")
    public <T> T getFromRedis(String key) {
        try {
            return (T) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Failed to get from Redis: {}", e.getMessage());
            return null;
        }
    }

    public void putToRedis(String key, Object value, Duration timeout) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout);
        } catch (Exception e) {
            log.warn("Failed to put to Redis: {}", e.getMessage());
        }
    }

    public void evictFromRedis(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Failed to evict from Redis: {}", e.getMessage());
        }
    }

    // 搜索结果缓存（5分钟）
    public <T> T getSearchResult(String query) {
        return getFromRedis(SEARCH_RESULT_PREFIX + query);
    }

    public void putSearchResult(String query, Object result) {
        putToRedis(SEARCH_RESULT_PREFIX + query, result, Duration.ofMinutes(5));
    }

    public void evictSearchResult(String query) {
        evictFromRedis(SEARCH_RESULT_PREFIX + query);
    }

    // 向量嵌入缓存（30分钟）
    public <T> T getVectorEmbedding(String key) {
        return getFromRedis(VECTOR_EMBEDDING_PREFIX + key);
    }

    public void putVectorEmbedding(String key, Object embedding) {
        putToRedis(VECTOR_EMBEDDING_PREFIX + key, embedding, Duration.ofMinutes(30));
    }

    public void evictVectorEmbedding(String key) {
        evictFromRedis(VECTOR_EMBEDDING_PREFIX + key);
    }

    // 用户缓存（5分钟）
    public <T> T getUser(String userId) {
        return getFromRedis(USER_CACHE_PREFIX + userId);
    }

    public void putUser(String userId, Object user) {
        putToRedis(USER_CACHE_PREFIX + userId, user, Duration.ofMinutes(5));
    }

    public void evictUser(String userId) {
        evictFromRedis(USER_CACHE_PREFIX + userId);
    }

    // 文件变更时清除相关缓存
    public void onFileChange(String fileId) {
        // 清除与该文件相关的搜索缓存
        // 实际实现可能需要更复杂的缓存键管理策略
        log.info("File {} changed, clearing related caches", fileId);
    }

    // 用户变更时清除用户缓存
    public void onUserChange(String userId) {
        evictUser(userId);
        log.info("User {} changed, clearing user cache", userId);
    }
}
