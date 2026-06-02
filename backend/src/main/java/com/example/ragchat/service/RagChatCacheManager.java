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
public class RagChatCacheManager {

    private final CacheManager springCacheManager;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SEARCH_RESULT_PREFIX = "search:result:";
    private static final String VECTOR_EMBEDDING_PREFIX = "vector:embedding:";
    private static final String USER_CACHE_PREFIX = "user:";

    public <T> T getFromLocalCache(String cacheName, String key, Callable<T> valueLoader) {
        long startTime = System.currentTimeMillis();
        log.debug("[RagChatCacheManager.getFromLocalCache] 开始 | cacheName={}, key={}", cacheName, key);

        try {
            Cache cache = springCacheManager.getCache(cacheName);
            if (cache != null) {
                T result = cache.get(key, valueLoader);
                log.debug("[RagChatCacheManager.getFromLocalCache] 本地缓存获取成功 | cacheName={}, key={}", cacheName, key);
                return result;
            }
            log.debug("[RagChatCacheManager.getFromLocalCache] 缓存不存在，执行值加载器 | cacheName={}, key={}", cacheName, key);
            return valueLoader.call();
        } catch (Exception e) {
            log.error("[RagChatCacheManager.getFromLocalCache] 异常 | cacheName={}, key={}, error={}", cacheName, key, e.getMessage());
            throw new RuntimeException("Failed to load cache value", e);
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[RagChatCacheManager.getFromLocalCache] 完成 | cacheName={}, key={}, 耗时={}ms", cacheName, key, costTime);
        }
    }

    public void putToLocalCache(String cacheName, String key, Object value) {
        long startTime = System.currentTimeMillis();
        log.debug("[RagChatCacheManager.putToLocalCache] 开始 | cacheName={}, key={}, valueType={}",
                cacheName, key, value != null ? value.getClass().getSimpleName() : "null");

        try {
            Cache cache = springCacheManager.getCache(cacheName);
            if (cache != null) {
                cache.put(key, value);
                log.debug("[RagChatCacheManager.putToLocalCache] 本地缓存设置成功 | cacheName={}, key={}", cacheName, key);
            }
        } catch (Exception e) {
            log.error("[RagChatCacheManager.putToLocalCache] 异常 | cacheName={}, key={}, error={}", cacheName, key, e.getMessage());
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[RagChatCacheManager.putToLocalCache] 完成 | cacheName={}, key={}, 耗时={}ms", cacheName, key, costTime);
        }
    }

    public void evictLocalCache(String cacheName, String key) {
        long startTime = System.currentTimeMillis();
        log.debug("[RagChatCacheManager.evictLocalCache] 开始 | cacheName={}, key={}", cacheName, key);

        try {
            Cache cache = springCacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
                log.debug("[RagChatCacheManager.evictLocalCache] 本地缓存清除成功 | cacheName={}, key={}", cacheName, key);
            }
        } catch (Exception e) {
            log.error("[RagChatCacheManager.evictLocalCache] 异常 | cacheName={}, key={}, error={}", cacheName, key, e.getMessage());
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[RagChatCacheManager.evictLocalCache] 完成 | cacheName={}, key={}, 耗时={}ms", cacheName, key, costTime);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getFromRedis(String key) {
        long startTime = System.currentTimeMillis();
        log.debug("[RagChatCacheManager.getFromRedis] 开始 | key={}", key);

        try {
            T result = (T) redisTemplate.opsForValue().get(key);
            log.debug("[RagChatCacheManager.getFromRedis] 获取成功 | key={}, exists={}", key, result != null);
            return result;
        } catch (Exception e) {
            log.warn("[RagChatCacheManager.getFromRedis] 获取失败 | key={}, error={}", key, e.getMessage());
            return null;
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[RagChatCacheManager.getFromRedis] 完成 | key={}, 耗时={}ms", key, costTime);
        }
    }

    public void putToRedis(String key, Object value, Duration timeout) {
        long startTime = System.currentTimeMillis();
        log.debug("[RagChatCacheManager.putToRedis] 开始 | key={}, timeout={}", key, timeout);

        try {
            redisTemplate.opsForValue().set(key, value, timeout);
            log.debug("[RagChatCacheManager.putToRedis] 设置成功 | key={}", key);
        } catch (Exception e) {
            log.warn("[RagChatCacheManager.putToRedis] 设置失败 | key={}, error={}", key, e.getMessage());
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[RagChatCacheManager.putToRedis] 完成 | key={}, 耗时={}ms", key, costTime);
        }
    }

    public void evictFromRedis(String key) {
        long startTime = System.currentTimeMillis();
        log.debug("[RagChatCacheManager.evictFromRedis] 开始 | key={}", key);

        try {
            redisTemplate.delete(key);
            log.debug("[RagChatCacheManager.evictFromRedis] 清除成功 | key={}", key);
        } catch (Exception e) {
            log.warn("[RagChatCacheManager.evictFromRedis] 清除失败 | key={}, error={}", key, e.getMessage());
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[RagChatCacheManager.evictFromRedis] 完成 | key={}, 耗时={}ms", key, costTime);
        }
    }

    public <T> T getSearchResult(String query) {
        String key = SEARCH_RESULT_PREFIX + query;
        log.debug("[RagChatCacheManager.getSearchResult] 获取搜索结果缓存 | queryLength={}", query != null ? query.length() : 0);
        return getFromRedis(key);
    }

    public void putSearchResult(String query, Object result) {
        String key = SEARCH_RESULT_PREFIX + query;
        log.debug("[RagChatCacheManager.putSearchResult] 设置搜索结果缓存 | queryLength={}", query != null ? query.length() : 0);
        putToRedis(key, result, Duration.ofMinutes(5));
    }

    public void evictSearchResult(String query) {
        String key = SEARCH_RESULT_PREFIX + query;
        log.debug("[RagChatCacheManager.evictSearchResult] 清除搜索结果缓存 | queryLength={}", query != null ? query.length() : 0);
        evictFromRedis(key);
    }

    public <T> T getVectorEmbedding(String key) {
        String cacheKey = VECTOR_EMBEDDING_PREFIX + key;
        log.debug("[RagChatCacheManager.getVectorEmbedding] 获取向量嵌入缓存 | key={}", key);
        return getFromRedis(cacheKey);
    }

    public void putVectorEmbedding(String key, Object embedding) {
        String cacheKey = VECTOR_EMBEDDING_PREFIX + key;
        log.debug("[RagChatCacheManager.putVectorEmbedding] 设置向量嵌入缓存 | key={}", key);
        putToRedis(cacheKey, embedding, Duration.ofMinutes(30));
    }

    public void evictVectorEmbedding(String key) {
        String cacheKey = VECTOR_EMBEDDING_PREFIX + key;
        log.debug("[RagChatCacheManager.evictVectorEmbedding] 清除向量嵌入缓存 | key={}", key);
        evictFromRedis(cacheKey);
    }

    public <T> T getUser(String userId) {
        String key = USER_CACHE_PREFIX + userId;
        log.debug("[RagChatCacheManager.getUser] 获取用户缓存 | userId={}", userId);
        return getFromRedis(key);
    }

    public void putUser(String userId, Object user) {
        String key = USER_CACHE_PREFIX + userId;
        log.debug("[RagChatCacheManager.putUser] 设置用户缓存 | userId={}", userId);
        putToRedis(key, user, Duration.ofMinutes(5));
    }

    public void evictUser(String userId) {
        String key = USER_CACHE_PREFIX + userId;
        log.debug("[RagChatCacheManager.evictUser] 清除用户缓存 | userId={}", userId);
        evictFromRedis(key);
    }

    public void onFileChange(String fileId) {
        long startTime = System.currentTimeMillis();
        log.info("[RagChatCacheManager.onFileChange] 文件变更，清除相关缓存 | fileId={}", fileId);

        try {
            log.debug("[RagChatCacheManager.onFileChange] 清除文件相关的搜索缓存");
        } catch (Exception e) {
            log.error("[RagChatCacheManager.onFileChange] 异常 | fileId={}, error={}", fileId, e.getMessage());
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[RagChatCacheManager.onFileChange] 完成 | fileId={}, 耗时={}ms", fileId, costTime);
        }
    }

    public void onUserChange(String userId) {
        long startTime = System.currentTimeMillis();
        log.info("[RagChatCacheManager.onUserChange] 用户变更，清除用户缓存 | userId={}", userId);

        try {
            evictUser(userId);
        } catch (Exception e) {
            log.error("[RagChatCacheManager.onUserChange] 异常 | userId={}, error={}", userId, e.getMessage());
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[RagChatCacheManager.onUserChange] 完成 | userId={}, 耗时={}ms", userId, costTime);
        }
    }
}
