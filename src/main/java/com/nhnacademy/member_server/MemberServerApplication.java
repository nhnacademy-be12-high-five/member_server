package com.nhnacademy.member_server;

import com.nhnacademy.member_server.entity.PointPolicy;
import com.nhnacademy.member_server.repository.PointPolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
@Slf4j
public class MemberServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemberServerApplication.class, args);
    }

    // 서버 켜질 때 한 번 실행
    @Bean
    public CommandLineRunner initData(PointPolicyRepository policyRepository) {
        return args -> {
            if (policyRepository.count() > 0) {
                log.info("[Init] 포인트 정책이 이미 존재하여 초기화 스킵.");
                return;
            }

            try {
                PointPolicy defaultPolicy = PointPolicy.builder()
                        .signupPoint(5000)
                        .reviewPoint(200)
                        .photoPoint(500)
                        .build();

                policyRepository.save(defaultPolicy);

                log.info("[Init] 기본 포인트 정책이 존재하지 않아 신규 정책 1건 생성 완료.");
            } catch (Exception e) {
                log.warn("[Init] 다른 인스터스에 의해 초기화 중복 또는 오류로 인한 실패 (중복일 가능성 높음): {}", e.getMessage());
            }
        };
    }
}
