package com.nhnacademy.member_server.config;

import com.nhnacademy.member_server.entity.PointPolicy;
import com.nhnacademy.member_server.repository.PointPolicyRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class InitDataConfig {

    @Bean
    public CommandLineRunner initData(PointPolicyRepository policyRepository) {
        return args -> {
            if (policyRepository.count() == 0) {
                PointPolicy defaultPolicy = PointPolicy.builder()
                        .signupPoint(5000)
                        .reviewPoint(200)
                        .photoPoint(500)
                        .build();
                policyRepository.save(defaultPolicy);
            }
        };
    }
}
