package com.nhnacademy.member_server.scheduler;

import com.nhnacademy.member_server.entity.member.Grade;
import com.nhnacademy.member_server.entity.member.Member;
import com.nhnacademy.member_server.entity.member.Status;
import com.nhnacademy.member_server.feign.OrderFeignClient;
import com.nhnacademy.member_server.repository.GradeRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GradeSchedulerTest {

    @InjectMocks
    GradeScheduler gradeScheduler;

    @Mock
    MemberRepository memberRepository;

    @Mock
    GradeRepository gradeRepository;

    @Mock
    OrderFeignClient orderClient;

    @Test
    @DisplayName("등급 산정 테스트 - 등급 상향 (GENERAL -> GOLD)")
    void updateMemberGrades_UpgradeTest() {
        // given
        // 리스트 정렬(sort)이 가능하도록 ArrayList 사용
        Grade general = Grade.builder().id(1L).gradeName("GENERAL").min(0).build();
        Grade gold = Grade.builder().id(2L).gradeName("GOLD").min(100000).build();
        List<Grade> grades = new ArrayList<>(List.of(general, gold));

        Member member = Member.builder()
                .id(1L)
                .grade(general) // 현재 등급 GENERAL
                .status(Status.ACTIVE)
                .build();

        given(gradeRepository.findAll()).willReturn(grades);
        given(memberRepository.findByLastLoginAtBeforeAndStatus(any(), eq(Status.ACTIVE)))
                .willReturn(List.of(member));

        // 주문 금액 150,000원 -> GOLD 기준(100,000) 충족
        given(orderClient.getTotalAmount(eq(1L), any(LocalDateTime.class)))
                .willReturn(ResponseEntity.ok(150000L));

        // when
        gradeScheduler.updateMemberGrades();

        // then
        assertThat(member.getGrade().getGradeName()).isEqualTo("GOLD");
    }

    @Test
    @DisplayName("등급 산정 테스트 - 등급 유지 (주문 금액 부족)")
    void updateMemberGrades_NoChangeTest() {
        // given
        Grade general = Grade.builder().id(1L).gradeName("GENERAL").min(0).build();
        Grade gold = Grade.builder().id(2L).gradeName("GOLD").min(100000).build();
        List<Grade> grades = new ArrayList<>(List.of(general, gold));

        Member member = Member.builder()
                .id(1L)
                .grade(general)
                .status(Status.ACTIVE)
                .build();

        given(gradeRepository.findAll()).willReturn(grades);
        given(memberRepository.findByLastLoginAtBeforeAndStatus(any(), eq(Status.ACTIVE)))
                .willReturn(List.of(member));

        // 주문 금액 50,000원 -> GOLD 기준 미달 -> GENERAL 유지
        given(orderClient.getTotalAmount(eq(1L), any(LocalDateTime.class)))
                .willReturn(ResponseEntity.ok(50000L));

        // when
        gradeScheduler.updateMemberGrades();

        // then
        assertThat(member.getGrade().getGradeName()).isEqualTo("GENERAL");
    }

    @Test
    @DisplayName("등급 산정 테스트 - 주문 서버 통신 실패 시 스킵")
    void updateMemberGrades_OrderApiFailTest() {
        // given
        Grade general = Grade.builder().id(1L).gradeName("GENERAL").min(0).build();
        List<Grade> grades = new ArrayList<>(List.of(general));

        Member member = Member.builder().id(1L).grade(general).build();

        given(gradeRepository.findAll()).willReturn(grades);
        given(memberRepository.findByLastLoginAtBeforeAndStatus(any(), eq(Status.ACTIVE)))
                .willReturn(List.of(member));

        // 주문 서버 에러 발생
        given(orderClient.getTotalAmount(any(), any())).willThrow(new RuntimeException("Connection Error"));

        // when
        gradeScheduler.updateMemberGrades();

        // then
        // 예외가 발생해도 스케줄러가 중단되지 않고, 멤버의 등급은 유지되어야 함
        assertThat(member.getGrade().getGradeName()).isEqualTo("GENERAL");
    }

    @Test
    @DisplayName("등급 산정 테스트 - 주문 금액 null일 경우 0으로 처리")
    void updateMemberGrades_NullAmountTest() {
        // given
        Grade general = Grade.builder().id(1L).gradeName("GENERAL").min(0).build();
        List<Grade> grades = new ArrayList<>(List.of(general));

        Member member = Member.builder().id(1L).grade(general).build();

        given(gradeRepository.findAll()).willReturn(grades);
        given(memberRepository.findByLastLoginAtBeforeAndStatus(any(), eq(Status.ACTIVE)))
                .willReturn(List.of(member));

        // 주문 금액이 null로 옴
        given(orderClient.getTotalAmount(eq(1L), any())).willReturn(ResponseEntity.ok(null));

        // when
        gradeScheduler.updateMemberGrades();

        // then
        assertThat(member.getGrade().getGradeName()).isEqualTo("GENERAL");
    }
}