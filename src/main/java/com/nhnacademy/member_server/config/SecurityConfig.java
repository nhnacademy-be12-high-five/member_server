package com.nhnacademy.member_server.config;

import com.nhnacademy.member_server.filter.GatewayAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GatewayAuthenticationFilter gatewayAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception{
        // csrf 설정 끔 -> 이유 원래는 자동으로 쿠키를 보내버리는 것 때문에 난수표를 같이 보내 csrf 공격을 방지하는 건데 우리는 쿠키를 인증수단으로 쓰지 않기 때문에 꺼도 됨
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        // STATELESS를 설정하면 매번 요청마다 확인해서 authentication 객체를 생성해줌
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/cart/**").permitAll()
                        .requestMatchers("/**").permitAll()
                        .anyRequest().authenticated())
                // 필터 검사 이전에 내가 만든 필터로 이미 로그인 된 회원은 로그인했어라고 알려줌
                .addFilterBefore(gatewayAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
