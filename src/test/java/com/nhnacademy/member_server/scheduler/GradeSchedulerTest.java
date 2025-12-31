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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

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
        Grade general = Grade.builder().id(1L).gradeName("GENERAL").min(0).build();
        Grade gold = Grade.builder().id(2L).gradeName("GOLD").min(100000).build();
        // 정렬 로직 테스트를 위해 순서를 섞어서 제공 (Service 내부에서 min 기준 내림차순 정렬함)
        List<Grade> grades = new ArrayList<>(List.of(general, gold));

        Member member = Member.builder()
                .id(1L)
                .grade(general) // 현재 등급 GENERAL
                .status(Status.ACTIVE)
                .build();

        given(gradeRepository.findAll()).willReturn(grades);

        // 페이징 처리 Mocking
        // 첫 번째 페이지(0): 멤버 반환
        Pageable pageable0 = PageRequest.of(0, 1000);
        given(memberRepository.findAllByStatus(eq(Status.ACTIVE), eq(pageable0)))
                .willReturn(new PageImpl<>(List.of(member)));

        // 두 번째 페이지(1): 빈 페이지 반환 (Loop 종료 조건)
        Pageable pageable1 = PageRequest.of(1, 1000);
        given(memberRepository.findAllByStatus(eq(Status.ACTIVE), eq(pageable1)))
                .willReturn(Page.empty());

        // 주문 서버 대량 조회 Mocking (Map<MemberId, TotalAmount>)
        // 주문 금액 150,000원 -> GOLD 기준(100,000) 충족
        Map<Long, Long> orderStats = Map.of(1L, 150000L);
        given(orderClient.getBulkTotalAmounts(any(), any(LocalDateTime.class)))
                .willReturn(ResponseEntity.ok(orderStats));

        // when
        gradeScheduler.updateMemberGrades();

        // then
        assertThat(member.getGrade().getGradeName()).isEqualTo("GOLD");
        // 변경사항이 있으므로 saveAll이 호출되어야 함
        verify(memberRepository, times(1)).saveAll(anyList());
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

        // 페이징 Mocking
        Pageable pageable0 = PageRequest.of(0, 1000);
        given(memberRepository.findAllByStatus(eq(Status.ACTIVE), eq(pageable0)))
                .willReturn(new PageImpl<>(List.of(member)));

        Pageable pageable1 = PageRequest.of(1, 1000);
        given(memberRepository.findAllByStatus(eq(Status.ACTIVE), eq(pageable1)))
                .willReturn(Page.empty());

        // 주문 금액 50,000원 -> GOLD 기준 미달 -> GENERAL 유지
        Map<Long, Long> orderStats = Map.of(1L, 50000L);
        given(orderClient.getBulkTotalAmounts(any(), any(LocalDateTime.class)))
                .willReturn(ResponseEntity.ok(orderStats));

        // when
        gradeScheduler.updateMemberGrades();

        // then
        assertThat(member.getGrade().getGradeName()).isEqualTo("GENERAL");
        // 변경사항이 없으므로 saveAll은 호출되지 않아야 함 (updateBatch 내부 로직상)
        verify(memberRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("등급 산정 테스트 - 주문 서버 통신 실패 시 해당 페이지 스킵")
    void updateMemberGrades_OrderApiFailTest() {
        // given
        Grade general = Grade.builder().id(1L).gradeName("GENERAL").min(0).build();
        List<Grade> grades = new ArrayList<>(List.of(general));

        Member member = Member.builder().id(1L).grade(general).build();

        given(gradeRepository.findAll()).willReturn(grades);

        // 페이징 Mocking
        Pageable pageable0 = PageRequest.of(0, 1000);
        given(memberRepository.findAllByStatus(eq(Status.ACTIVE), eq(pageable0)))
                .willReturn(new PageImpl<>(List.of(member)));

        Pageable pageable1 = PageRequest.of(1, 1000);
        given(memberRepository.findAllByStatus(eq(Status.ACTIVE), eq(pageable1)))
                .willReturn(Page.empty());

        // 주문 서버 에러 발생
        given(orderClient.getBulkTotalAmounts(any(), any()))
                .willThrow(new RuntimeException("Connection Error"));

        // when
        gradeScheduler.updateMemberGrades();

        // then
        // 예외가 발생(catch)하고 루프는 계속 돌지만, updateBatch는 호출되지 않거나 빈 stats로 호출됨
        // 코드 로직상 catch 블록에서 continue 하므로 updateBatch가 실행되지 않음 -> 등급 유지
        assertThat(member.getGrade().getGradeName()).isEqualTo("GENERAL");
        verify(memberRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("등급 산정 테스트 - 주문 데이터가 없을 경우(빈 Map) 0원으로 처리")
    void updateMemberGrades_EmptyStatsTest() {
        // given
        Grade general = Grade.builder().id(1L).gradeName("GENERAL").min(0).build();
        List<Grade> grades = new ArrayList<>(List.of(general));

        Member member = Member.builder().id(1L).grade(general).build();

        given(gradeRepository.findAll()).willReturn(grades);

        Pageable pageable0 = PageRequest.of(0, 1000);
        given(memberRepository.findAllByStatus(eq(Status.ACTIVE), eq(pageable0)))
                .willReturn(new PageImpl<>(List.of(member)));

        Pageable pageable1 = PageRequest.of(1, 1000);
        given(memberRepository.findAllByStatus(eq(Status.ACTIVE), eq(pageable1)))
                .willReturn(Page.empty());

        // 주문 데이터가 없어서 빈 Map 반환
        given(orderClient.getBulkTotalAmounts(any(), any()))
                .willReturn(ResponseEntity.ok(Collections.emptyMap()));

        // when
        gradeScheduler.updateMemberGrades();

        // then
        // getOrDefault(id, 0L)에 의해 0원으로 처리 -> GENERAL 유지
        assertThat(member.getGrade().getGradeName()).isEqualTo("GENERAL");
        verify(memberRepository, never()).saveAll(anyList());
    }
}