package com.nhnacademy.member_server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String,Object> template = new RedisTemplate<>();
        // yml에 설정한 redis ip와 포트를 연결
        template.setConnectionFactory(factory);

        // redis Key 저장할 때 String을 그대로 문자열로 저장한다는 뜻
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // redis Value 저장할 때 Json 으로 바꿔서 저장한다는 뜻
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        // 설정 내용 바탕으로 초기화 도구 반환
        template.afterPropertiesSet();
        return template;
    }
}
