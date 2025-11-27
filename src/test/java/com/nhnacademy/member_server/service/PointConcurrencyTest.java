package com.nhnacademy.member_server.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nhnacademy.member_server.dto.request.PointTransactionRequest;
import com.nhnacademy.member_server.entity.Grade;
import com.nhnacademy.member_server.entity.Member;
import com.nhnacademy.member_server.entity.Role;
import com.nhnacademy.member_server.entity.Status;
import com.nhnacademy.member_server.repository.GradeRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PointConcurrencyTest {

    @Autowired PointService pointService;
    @Autowired MemberRepository memberRepository;
    @Autowired GradeRepository gradeRepository;

    private Long memberId;

    @BeforeEach
    void setUp() {
        // 1. 등급 생성
        Grade grade = gradeRepository.save(Grade.builder()
                .gradeName("GENERAL").min(0).pointRate(new BigDecimal("0.01")).build());

        // 2. 회원 생성 (10000원 보유)
        Member member = memberRepository.save(Member.builder()
                .loginId("test").name("tester").password("1234").phone("010-0000-0000").email("test@test.com")
                .birthDate(LocalDate.now()).lastLoginAt(LocalDateTime.now())
                .status(Status.ACTIVE).role(Role.USER)
                .grade(grade)
                .currentPoint(10000L)
                .build());

        this.memberId = member.getId();
    }

    @Test
    @DisplayName("동시성 테스트: 100원을 1000명이 동시에 사용")
    void concurrentUsePoint() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(32); // 동시에 32개까지 일 처리
        CountDownLatch latch = new CountDownLatch(1000);

        for (int i = 0; i < 1000; i++) {
            long orderId = i + 1000L;
            executor.submit(() -> {
                try {
                    pointService.usePoint(new PointTransactionRequest(memberId, 100L, orderId));
                } finally {
                    latch.countDown(); // 작업 하나 끝날때 마다 카운터를 1씩 깎음
                }
            });
        }

        latch.await(); // 0이 되기 전까지 block

        Member member = memberRepository.findById(memberId).orElseThrow();
        assertThat(member.getCurrentPoint()).isEqualTo(0L);
    }
}