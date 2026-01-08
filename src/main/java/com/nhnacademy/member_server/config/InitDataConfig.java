package com.nhnacademy.member_server.config;

import com.nhnacademy.member_server.entity.PointPolicy;
import com.nhnacademy.member_server.entity.member.Grade;
import com.nhnacademy.member_server.repository.GradeRepository;
import com.nhnacademy.member_server.repository.PointPolicyRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Configuration
@Profile("!test")
public class InitDataConfig {

    @Bean
    public CommandLineRunner initData(PointPolicyRepository policyRepository,
                                      GradeRepository gradeRepository) {
        return args -> {
            // 1. 포인트 정책 초기화
            if (policyRepository.count() == 0) {
                PointPolicy defaultPolicy = PointPolicy.builder()
                        .signupPoint(5000)
                        .reviewPoint(200)
                        .photoPoint(500)
                        .build();
                policyRepository.save(defaultPolicy);
            }

            // 2. 등급 정책 초기화 (각 등급별로 존재하는지 체크 후 생성)
            createGradeIfNotExists(gradeRepository, "GENERAL", 0, 100000, "0.01");
            createGradeIfNotExists(gradeRepository, "ROYAL", 100000, 200000, "0.02");
            createGradeIfNotExists(gradeRepository, "GOLD", 200000, 300000, "0.025");
            createGradeIfNotExists(gradeRepository, "PLATINUM", 300000, null, "0.03");

            System.out.println(">>> [INIT] 등급 데이터 점검 및 초기화 완료");
        };
    }

    @Transactional
    public void createGradeIfNotExists(GradeRepository gradeRepository,
                                       String name,
                                       int min,
                                       Integer max,
                                       String rate) {
        if (gradeRepository.findByGradeName(name).isPresent()) {
            return;
        }

        gradeRepository.save(Grade.builder()
                .gradeName(name)
                .min(min)
                .max(max)
                .pointRate(new BigDecimal(rate))
                .build());
    }
}