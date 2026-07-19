package me.ifmo.backend.shared.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Logging cache error handler")
class LoggingCacheErrorHandlerTest {

    @Test
    @DisplayName("keeps application flow alive for every failed cache operation")
    void keepsApplicationFlowAliveForCacheFailures() {
        var handler = new LoggingCacheErrorHandler();
        var cache = mock(Cache.class);
        var failure = new IllegalStateException("Redis is unavailable");
        when(cache.getName()).thenReturn(CacheNames.CATALOG_SEARCH);

        assertThatCode(() -> {
            handler.handleCacheGetError(failure, cache, "key");
            handler.handleCachePutError(failure, cache, "key", "value");
            handler.handleCacheEvictError(failure, cache, "key");
            handler.handleCacheClearError(failure, cache);
        }).doesNotThrowAnyException();
    }
}
