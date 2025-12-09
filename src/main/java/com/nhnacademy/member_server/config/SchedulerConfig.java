package com.nhnacademy.member_server.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling // 스케줄러 켜기
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S") // 락 기본 최대 유지 시간 (30초)
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        // 우리가 쓰는 Redis에 락 정보를 저장하겠다!
        return new RedisLockProvider(connectionFactory);
    }
}