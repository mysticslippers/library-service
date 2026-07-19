package me.ifmo.backend.shared.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

@Slf4j
public class LoggingCacheErrorHandler implements CacheErrorHandler {

    private void logFailure(String operation, Cache cache, RuntimeException exception) {
        log.warn(
                "cache_operation={} cache={} outcome=fallback error_type={}",
                operation,
                cache.getName(),
                exception.getClass().getSimpleName()
        );
        log.debug("Cache operation failed", exception);
    }

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        logFailure("get", cache, exception);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        logFailure("put", cache, exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        logFailure("evict", cache, exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        logFailure("clear", cache, exception);
    }
}
