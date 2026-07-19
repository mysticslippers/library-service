package me.ifmo.backend.shared.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
            @Value("${cache.catalog-search.ttl:90s}") Duration catalogSearchTtl,
            @Value("${cache.catalog-search.key-prefix:library-service-backend:local:}") String keyPrefix) {

        var valueSerializer = new GenericJackson2JsonRedisSerializer();
        var catalogSearchConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(catalogSearchTtl).disableCachingNullValues()
                .computePrefixWith(cacheName -> keyPrefix + cacheName + "::")
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

        var cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory, BatchStrategies.scan(1_000));

        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(catalogSearchConfiguration).withInitialCacheConfigurations(Map.of(
                        CacheNames.CATALOG_SEARCH,
                        catalogSearchConfiguration
                ))
                .transactionAware().enableStatistics().build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
