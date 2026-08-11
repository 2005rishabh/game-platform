package com.rishabh.game_platform.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        // StringRedisTemplate is perfect for queue since it only need to store
        // Usernames/Player IDs
        return new StringRedisTemplate(connectionFactory);
    }
}