package com.nhnacademy.member_server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RedisConfig.class)
            .withPropertyValues(
                    "spring.data.redis.host=localhost",
                    "spring.data.redis.port=6379",
                    "spring.data.redis.password=",
                    "spring.data.redis.database=0"
            );

    @Test
    @DisplayName("RedisConfig 빈 등록 확인")
    void redisConfigLoads() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RedisConnectionFactory.class);
            assertThat(context).hasSingleBean(RedisTemplate.class);
            assertThat(context).hasSingleBean(CacheManager.class);
        });
    }
}