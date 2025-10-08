package com.foodsave.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(ObjectMapper objectMapper) {
        ObjectMapper mapper = objectMapper.copy();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);
        serializer.setObjectMapper(mapper);

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();
    }

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory,
                                               RedisCacheConfiguration redisCacheConfiguration) {
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Долгосрочное кэширование (2 часа) - для статических данных
        RedisCacheConfiguration longTermConfig = redisCacheConfiguration.entryTtl(Duration.ofHours(2));
        cacheConfigurations.put("categories", longTermConfig);
        cacheConfigurations.put("stores", longTermConfig);
        cacheConfigurations.put("storesList", longTermConfig);
        
        // Среднесрочное кэширование (30 минут) - для динамических данных
        RedisCacheConfiguration mediumTermConfig = redisCacheConfiguration.entryTtl(Duration.ofMinutes(30));
        cacheConfigurations.put("products", mediumTermConfig);
        cacheConfigurations.put("featuredProducts", mediumTermConfig);
        cacheConfigurations.put("productsByStore", mediumTermConfig);
        cacheConfigurations.put("productsByCategory", mediumTermConfig);
        cacheConfigurations.put("discountedProducts", mediumTermConfig);
        cacheConfigurations.put("productCategoriesCache", mediumTermConfig);
        
        // Краткосрочное кэширование (5 минут) - для часто изменяющихся данных
        RedisCacheConfiguration shortTermConfig = redisCacheConfiguration.entryTtl(Duration.ofMinutes(5));
        cacheConfigurations.put("searchResults", shortTermConfig);
        cacheConfigurations.put("userOrders", shortTermConfig);
        cacheConfigurations.put("orderStats", shortTermConfig);
        cacheConfigurations.put("lowStockProducts", shortTermConfig);

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
