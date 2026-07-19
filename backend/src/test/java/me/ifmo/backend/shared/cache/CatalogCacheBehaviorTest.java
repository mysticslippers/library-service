package me.ifmo.backend.shared.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(CatalogCacheBehaviorTest.TestConfig.class)
@DisplayName("Catalog cache behavior")
class CatalogCacheBehaviorTest {

    @Autowired
    private CachedFixture fixture;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCache() {
        fixture.reset();
        cacheManager.getCache(CacheNames.CATALOG_SEARCH).clear();
    }

    @Test
    @DisplayName("reuses cached values and clears them through the composed annotation")
    void reusesAndInvalidatesCachedValues() {
        assertThat(fixture.load("key")).isEqualTo("value-1");
        assertThat(fixture.load("key")).isEqualTo("value-1");
        assertThat(fixture.invocations()).isEqualTo(1);

        fixture.changeCatalog();

        assertThat(fixture.load("key")).isEqualTo("value-2");
        assertThat(fixture.invocations()).isEqualTo(2);
    }

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheNames.CATALOG_SEARCH);
        }

        @Bean
        CachedFixture cachedFixture() {
            return new CachedFixture();
        }
    }

    static class CachedFixture {

        private final AtomicInteger invocationCount = new AtomicInteger();

        @Cacheable(cacheNames = CacheNames.CATALOG_SEARCH, key = "#key")
        public String load(String key) {
            return "value-" + invocationCount.incrementAndGet();
        }

        @InvalidateCatalogSearch
        public void changeCatalog() {
        }

        public int invocations() {
            return invocationCount.get();
        }

        public void reset() {
            invocationCount.set(0);
        }
    }
}
