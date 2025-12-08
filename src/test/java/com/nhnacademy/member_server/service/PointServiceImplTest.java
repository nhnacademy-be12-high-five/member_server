package com.nhnacademy.member_server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhnacademy.member_server.dto.request.PointAdminAdjustmentRequest;
import com.nhnacademy.member_server.dto.request.PointAdminPolicyRequest;
import com.nhnacademy.member_server.dto.request.PointEarnRequest;
import com.nhnacademy.member_server.dto.request.PointTransactionRequest;
import com.nhnacademy.member_server.dto.response.PointAdminPolicyResponse;
import com.nhnacademy.member_server.dto.response.PointBalanceResponse;
import com.nhnacademy.member_server.dto.response.PointHistoryResponse;
import com.nhnacademy.member_server.entity.Grade;
import com.nhnacademy.member_server.entity.Member;
import com.nhnacademy.member_server.entity.PointEventType;
import com.nhnacademy.member_server.entity.PointHistory;
import com.nhnacademy.member_server.entity.PointPolicy;
import com.nhnacademy.member_server.entity.Role;
import com.nhnacademy.member_server.entity.Status;
import com.nhnacademy.member_server.exception.BusinessException;
import com.nhnacademy.member_server.exception.ErrorCode;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.repository.PointHistoryRepository;
import com.nhnacademy.member_server.repository.PointPolicyRepository;
import com.nhnacademy.member_server.service.impl.PointServiceImpl;
import java.util.List;
import java.util.Optional;
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

@ExtendWith(MockitoExtension.class)
class PointServiceImplTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @Mock
    private PointPolicyRepository pointPolicyRepository;

    @InjectMocks
    private PointServiceImpl pointServiceImpl;

    // POINT INTERNAL API
    @Test
    @DisplayName("포인트 사용 성공 테스트")
    void usePoints_Success() {
        // given
        Long memberId = 1L;
        Long useAmount = 5000L;
        Member member = createMember(memberId, 10000L);

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));

        PointTransactionRequest request = new PointTransactionRequest(memberId, useAmount, 1L);

        // when
        Long remainPoint = pointServiceImpl.usePoint(request);

        // then
        assertThat(remainPoint).isEqualTo(5000L);
        assertThat(member.getCurrentPoint()).isEqualTo(5000L);

        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("포인트 잔액 부족 실패 테스트")
    void usePoints_Fail_NotEnough() {
        // given
        Long memberId = 1L;
        Member member = createMember(memberId, 1000L);;

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));

        PointTransactionRequest request = new PointTransactionRequest(memberId, 5000L, 1L);

        // when & then
        assertThatThrownBy(() -> pointServiceImpl.usePoint(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_NOT_ENOUGH);
    }

    @Test
    @DisplayName("포인트 환불 테스트")
    void revertPoints_Success() {
        // given
        Long memberId = 1L;
        Member member = createMember(memberId, 5000L);

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));

        PointTransactionRequest request = new PointTransactionRequest(memberId, 1000L, 1L);

        // when
        Long result = pointServiceImpl.revertPoint(request);

        // then
        assertThat(result).isEqualTo(6000L); // 5000 + 1000

        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("포인트 주문 적립 성공 테스트 (등급 비율)")
    void earnPoints_Order_Success() {
        // given
        Long memberId = 1L;
        Member member = createMember(memberId, 10000L);

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));

        PointEarnRequest request = PointEarnRequest.builder()
                .memberId(memberId)
                .eventType(PointEventType.EARN_ORDER)
                .pureAmount(50000L)
                .orderId(1L)
                .build();

        // when
        Long result = pointServiceImpl.earnPoint(request);

        // then
        assertThat(result).isEqualTo(10500L); // 10000 + 500
        assertThat(member.getCurrentPoint()).isEqualTo(10500L);

        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("포인트 적립 성공 테스트 (고정값)")
    void earnPoints_Review_Success() {
        // given
        Long memberId = 1L;
        Member member = createMember(memberId, 10000L);

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));

        PointPolicy policy = PointPolicy.builder()
                .signupPoint(5000)
                .reviewPoint(200)
                .photoPoint(500)
                .build();

        when(pointPolicyRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(policy);

        PointEarnRequest request = PointEarnRequest.builder()
                .memberId(memberId)
                .eventType(PointEventType.EARN_REVIEW)
                .pureAmount(null)
                .orderId(null)
                .build();

        // when
        Long result = pointServiceImpl.earnPoint(request);

        // then
        assertThat(result).isEqualTo(10200L);

        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    // Point History API
    @Test
    @DisplayName("포인트 잔액 조회 성공")
    void getBalance_Success() {
        Long memberId = 1L;
        Member member = createMember(memberId, 5000L);

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(pointHistoryRepository.sumEarnedPoints(memberId)).thenReturn(15000L);

        PointBalanceResponse response = pointServiceImpl.getBalance(memberId);

        assertThat(response.getMemberId()).isEqualTo(memberId);
        assertThat(response.getCurrentPoint()).isEqualTo(5000L);
        assertThat(response.getTotalEarnedPoint()).isEqualTo(15000L);
    }

    @Test
    @DisplayName("포인트 이력 조회 성공")
    void getHistory_Success() {
        Long memberId = 1L;
        Pageable pageable = PageRequest.of(0, 10);

        Member member = createMember(memberId, 10000L);

        PointHistory history = PointHistory.builder()
                .orderId(123L)
                .member(member)
                .amount(500L)
                .description("적립")
                .pointEventType(PointEventType.EARN_ORDER)
                .pointBalance(10500L)
                .build();

        Page<PointHistory> historyPage = new PageImpl<>(List.of(history));

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(pointHistoryRepository.findAllByMemberId(memberId, pageable)).thenReturn(historyPage);

        Page<PointHistoryResponse> result = pointServiceImpl.getHistory(memberId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getAmount()).isEqualTo(500L);
        assertThat(result.getTotalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("최신 포인트 정책 조회 성공")
    void getRecentPolicy_Success() {
        PointPolicy policy = PointPolicy.builder()
                .signupPoint(5000)
                .reviewPoint(200)
                .photoPoint(500)
                .build();
        when(pointPolicyRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(policy);

        PointAdminPolicyResponse response = pointServiceImpl.getRecentPolicy();

        assertThat(response.getSignupPoint()).isEqualTo(5000);
        assertThat(response.getReviewPoint()).isEqualTo(200);
    }

    @Test
    @DisplayName("정책 조회 실패 - 데이터 없음")
    void getRecentPolicy_Fail_NotFound() {
        when(pointPolicyRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(null);

        assertThatThrownBy(() -> pointServiceImpl.getRecentPolicy())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_NOT_POLICY);
    }

    // Point Admin API
    @Test
    @DisplayName("포인트 정책 수정(등록) 성공")
    void updatePolicy_Success() {
        PointAdminPolicyRequest request = new PointAdminPolicyRequest(10000, 300, 600);

        pointServiceImpl.updatePolicy(request);

        verify(pointPolicyRepository).save(any(PointPolicy.class));
    }

    @Test
    @DisplayName("관리자 포인트 수동 지급 성공")
    void manualAdjustment_Earn_Success() {
        Long memberId = 1L;
        Member member = createMember(memberId, 0L);

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));

        PointAdminAdjustmentRequest request = new PointAdminAdjustmentRequest(memberId, 5000L, "보상");

        Long result = pointServiceImpl.adjustmentMemberPoint(request);

        assertThat(result).isEqualTo(5000L);
        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("관리자 포인트 수동 차감 성공")
    void manualAdjustment_Use_Success() {
        Long memberId = 1L;
        Member member = createMember(memberId, 10000L);

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));

        PointAdminAdjustmentRequest request = new PointAdminAdjustmentRequest(memberId, -5000L, "회수");

        Long result = pointServiceImpl.adjustmentMemberPoint(request);

        assertThat(result).isEqualTo(5000L);
        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("관리자 수동 차감 실패 - 잔액 부족")
    void manualAdjustment_Use_Fail_NotEnough() {
        Long memberId = 1L;
        Member member = createMember(memberId, 1000L);

        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));

        PointAdminAdjustmentRequest request = new PointAdminAdjustmentRequest(memberId, -5000L, "회수");

        assertThatThrownBy(() -> pointServiceImpl.adjustmentMemberPoint(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_NOT_ENOUGH);
    }

    private Member createMember(Long id, Long point) {
        Grade grade = Grade.builder()
                .gradeName("GENERAL")
                .min(0)
                .max(100000)
                .pointRate(java.math.BigDecimal.valueOf(0.01))
                .build();

        return Member.builder()
                .id(id)
                .loginId("test_user_" + id)
                .name("김유저")
                .currentPoint(point)
                .role(Role.USER)
                .status(Status.ACTIVE)
                .grade(grade)
                .build();
    }
}