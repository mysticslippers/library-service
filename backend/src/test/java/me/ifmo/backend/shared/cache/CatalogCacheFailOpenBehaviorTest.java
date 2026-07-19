package me.ifmo.backend.shared.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(CatalogCacheFailOpenBehaviorTest.TestConfig.class)
@DisplayName("Catalog cache fail-open behavior")
class CatalogCacheFailOpenBehaviorTest {

    @Autowired
    private CachedFixture fixture;

    @Test
    @DisplayName("loads data normally when every cache operation fails")
    void loadsDataWhenCacheOperationsFail() {
        assertThat(fixture.load("key")).isEqualTo("database-value-1");
        assertThat(fixture.load("key")).isEqualTo("database-value-2");
    }

    @Configuration
    @EnableCaching
    static class TestConfig implements CachingConfigurer {

        @Bean
        @Override
        public CacheManager cacheManager() {
            return new FailingCacheManager();
        }

        @Override
        public CacheErrorHandler errorHandler() {
            return new LoggingCacheErrorHandler();
        }

        @Bean
        CachedFixture cachedFixture() {
            return new CachedFixture();
        }
    }

    static class CachedFixture {

        private final AtomicInteger invocationCount = new AtomicInteger();

        @Cacheable(cacheNames = CacheNames.CATALOG_SEARCH, key = "#key", sync = true)
        public String load(String key) {
            return "database-value-" + invocationCount.incrementAndGet();
        }
    }

    static class FailingCacheManager implements CacheManager {

        private final Cache cache = new FailingCache();

        @Override
        @Nullable
        public Cache getCache(String name) {
            return cache;
        }

        @Override
        public Collection<String> getCacheNames() {
            return List.of(CacheNames.CATALOG_SEARCH);
        }
    }

    static class FailingCache implements Cache {

        @Override
        public String getName() {
            return CacheNames.CATALOG_SEARCH;
        }

        @Override
        public Object getNativeCache() {
            return this;
        }

        @Override
        @Nullable
        public ValueWrapper get(Object key) {
            throw unavailable();
        }

        @Override
        @Nullable
        public <T> T get(Object key, @Nullable Class<T> type) {
            throw unavailable();
        }

        @Override
        @Nullable
        public <T> T get(Object key, Callable<T> valueLoader) {
            throw unavailable();
        }

        @Override
        public void put(Object key, @Nullable Object value) {
            throw unavailable();
        }

        @Override
        @Nullable
        public ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
            throw unavailable();
        }

        @Override
        public void evict(Object key) {
            throw unavailable();
        }

        @Override
        public void clear() {
            throw unavailable();
        }

        private IllegalStateException unavailable() {
            return new IllegalStateException("Redis is unavailable");
        }
    }
}
