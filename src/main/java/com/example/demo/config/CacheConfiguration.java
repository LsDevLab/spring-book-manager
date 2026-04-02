package com.example.demo.config;

import com.github.benmanes.caffeine.cache.CaffeineSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;

import java.time.Duration;

@Configuration
class CacheConfiguration {

    @Bean
    @Primary
    public CacheManager caffeineCacheManager(
            @Value("${cache.caffeine.spec:maximumSize=100,expireAfterWrite=10m}") String caffeineSpec) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeineSpec(CaffeineSpec.parse(caffeineSpec));
        return cacheManager;
    }


    @Bean
    public <T> RedisTemplate<String, T> redisTemplate(@Autowired LettuceConnectionFactory lettuceConnectionFactory) {
        RedisTemplate<String, T> template = new RedisTemplate<>();
        template.setConnectionFactory(lettuceConnectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        RedisSerializer<Object> valueSerializer = RedisSerializer.json();

        template.setKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);

        template.setHashKeySerializer(keySerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory factory,
                                               @Value("${cache.redis.time-to-live:30m}") Duration ttl) {

        // Spring Data Redis 4.0 moved from Jackson 2 (com.fasterxml) to Jackson 3 (tools.jackson).
        // Old: GenericJackson2JsonRedisSerializer — deprecated, Jackson 2.
        // New: GenericJacksonJsonRedisSerializer — Jackson 3, uses JsonMapper (Jackson 3's ObjectMapper).
        //
        // enableDefaultTyping() — embeds "@class" type info in the JSON so on deserialization
        // Jackson knows which class to reconstruct (e.g., RestPage<Book>, Book, etc.).
        // Without it, Jackson deserializes everything as LinkedHashMap.
        //
        // Two options exist:
        //   enableDefaultTyping(validator) — validates types before deserializing (safer)
        //   enableUnsafeDefaultTyping()    — no validation (simpler, but vulnerable to code injection)
        // For a dev/learning project, unsafe is fine. In production, use a PolymorphicTypeValidator
        // that restricts which classes can be deserialized (prevents malicious payloads).
        RedisSerializer<Object> serializer = GenericJacksonJsonRedisSerializer.builder()
                .enableUnsafeDefaultTyping()
                .build();

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .entryTtl(ttl);

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }

}