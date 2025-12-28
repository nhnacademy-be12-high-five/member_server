package com.nhnacademy.member_server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.nhnacademy.member_server.dto.request.PointAdminAdjustmentRequest;
import com.nhnacademy.member_server.dto.request.PointAdminPolicyRequest;
import com.nhnacademy.member_server.dto.request.PointEarnRequest;
import com.nhnacademy.member_server.dto.request.PointTransactionRequest;
import com.nhnacademy.member_server.dto.response.PointAdminPolicyResponse;
import com.nhnacademy.member_server.dto.response.PointBalanceResponse;
import com.nhnacademy.member_server.dto.response.PointHistoryResponse;
import com.nhnacademy.member_server.entity.PointEventType;
import com.nhnacademy.member_server.entity.PointHistory;
import com.nhnacademy.member_server.entity.PointPolicy;
import com.nhnacademy.member_server.entity.PointStatus;
import com.nhnacademy.member_server.entity.member.Grade;
import com.nhnacademy.member_server.entity.member.Member;
import com.nhnacademy.member_server.exception.BusinessException;
import com.nhnacademy.member_server.exception.ErrorCode;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.repository.PointHistoryRepository;
import com.nhnacademy.member_server.repository.PointPolicyRepository;
import com.nhnacademy.member_server.service.impl.PointServiceImpl;
import java.math.BigDecimal;
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

    /**
     * 포인트 사용 (usePoint)
     */
    @Test
    @DisplayName("포인트 사용 성공")
    void usePoints_Success() {
        Long memberId = 1L;
        Member member = createMember(memberId, 10000L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        PointTransactionRequest request = new PointTransactionRequest(memberId, 5000L, 1L);

        Long result = pointServiceImpl.usePoint(request);

        assertThat(result).isEqualTo(5000L);
        assertThat(member.getCurrentPoint()).isEqualTo(5000L);
        then(pointHistoryRepository).should().save(any(PointHistory.class));
    }

    @Test
    @DisplayName("포인트 사용 실패 - 잔액 부족")
    void usePoints_Fail_NotEnough() {
        Long memberId = 1L;
        Member member = createMember(memberId, 1000L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        PointTransactionRequest request = new PointTransactionRequest(memberId, 5000L, 1L);

        assertThatThrownBy(() -> pointServiceImpl.usePoint(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.POINT_NOT_ENOUGH);
    }

    /**
     * 포인트 복구/환불 (revertPoint)
     */
    @Test
    @DisplayName("포인트 주문 취소 복구 성공")
    void revertPoints_Success() {
        Long memberId = 1L;
        Member member = createMember(memberId, 5000L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        PointTransactionRequest request = new PointTransactionRequest(memberId, 1000L, 1L);

        Long result = pointServiceImpl.revertPoint(request);

        assertThat(result).isEqualTo(6000L);
        // 상태 확인: PointEventType.USE_CANCEL_ORDER
        then(pointHistoryRepository).should().save(refEq(new PointHistory(
                1L, member, 1000L, "주문 취소로 인한 사용 포인트 복구 (주문번호: 1)", PointEventType.USE_CANCEL_ORDER, 6000L, PointStatus.CONFIRMED
        ), "createdAt", "id")); // id, createdAt은 무시
    }

    @Test
    @DisplayName("반품으로 인한 사용 포인트 환불 성공")
    void revertUsePointForReturn_Success() {
        Long memberId = 1L;
        Member member = createMember(memberId, 5000L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        PointTransactionRequest request = new PointTransactionRequest(memberId, 2000L, 100L);

        pointServiceImpl.revertUsePointForReturn(request);

        assertThat(member.getCurrentPoint()).isEqualTo(7000L);
        // PointEventType.USE_CANCEL_RETURN 확인
        then(pointHistoryRepository).should().save(refEq(new PointHistory(
                100L, member, 2000L, "반품으로 인한 사용 포인트 복구 (주문번호: 100)", PointEventType.USE_CANCEL_RETURN, 7000L, PointStatus.CONFIRMED
        ), "createdAt", "id"));
    }


    /**
     * 포인트 적립 (earnPoint)
     */
    @Test
    @DisplayName("주문 적립 성공 (등급 비율 적용)")
    void earnPoints_Order_Success() {
        Long memberId = 1L;
        Member member = createMember(memberId, 10000L);
        // Grade rate 1% 가정
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        PointEarnRequest request = new PointEarnRequest(memberId, PointEventType.EARN_ORDER, 50000L, 1L);

        Long result = pointServiceImpl.earnPoint(request);

        assertThat(result).isEqualTo(10500L); // 10000 + (50000 * 0.01)
        then(pointHistoryRepository).should().save(any(PointHistory.class));
    }

    @Test
    @DisplayName("반품 시 적립 포인트(EARN_REFUND) 성공")
    void earnPoints_Refund_Success() {
        Long memberId = 1L;
        Member member = createMember(memberId, 10000L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));
        // 중복 체크 통과
        given(pointHistoryRepository.existsByOrderIdAndPointEventType(100L, PointEventType.EARN_REFUND))
                .willReturn(false);

        PointEarnRequest request = new PointEarnRequest(memberId, PointEventType.EARN_REFUND, 500L, 100L);

        Long result = pointServiceImpl.earnPoint(request);

        assertThat(result).isEqualTo(10500L);
        then(pointHistoryRepository).should().save(any(PointHistory.class));
    }

    @Test
    @DisplayName("반품 시 적립 포인트 - 이미 적립된 내역이 있으면 스킵(Idempotency)")
    void earnPoints_Refund_Skip_Duplicate() {
        Long memberId = 1L;
        Member member = createMember(memberId, 10000L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));
        // 이미 존재함
        given(pointHistoryRepository.existsByOrderIdAndPointEventType(100L, PointEventType.EARN_REFUND))
                .willReturn(true);

        PointEarnRequest request = new PointEarnRequest(memberId, PointEventType.EARN_REFUND, 500L, 100L);

        Long result = pointServiceImpl.earnPoint(request);

        assertThat(result).isEqualTo(10000L); // 포인트 변동 없음
        then(pointHistoryRepository).should(never()).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("리뷰 작성 적립 성공 (정책 기반)")
    void earnPoints_Review_Success() {
        Long memberId = 1L;
        Member member = createMember(memberId, 10000L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        PointPolicy policy = PointPolicy.builder().reviewPoint(200).build();
        given(pointPolicyRepository.findTopByOrderByUpdatedAtDesc()).willReturn(policy);

        PointEarnRequest request = new PointEarnRequest(memberId, PointEventType.EARN_REVIEW, null, null);

        Long result = pointServiceImpl.earnPoint(request);

        assertThat(result).isEqualTo(10200L);
        then(pointHistoryRepository).should().save(any(PointHistory.class));
    }

    @Test
    @DisplayName("리뷰 업그레이드(일반->포토) 적립 성공")
    void earnPoints_ReviewUpgrade_Success() {
        Long memberId = 1L;
        Member member = createMember(memberId, 10000L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        // 포토 500, 일반 200 -> 차액 300 지급
        PointPolicy policy = PointPolicy.builder().reviewPoint(200).photoPoint(500).build();
        given(pointPolicyRepository.findTopByOrderByUpdatedAtDesc()).willReturn(policy);

        PointEarnRequest request = new PointEarnRequest(memberId, PointEventType.EARN_REVIEW_UPGRADE, null, null);

        pointServiceImpl.earnPoint(request);

        assertThat(member.getCurrentPoint()).isEqualTo(10300L);
    }

    @Test
    @DisplayName("적립금이 0원인 경우 저장하지 않고 현재 잔액 반환")
    void earnPoints_ZeroPoints_NoSave() {
        Long memberId = 1L;
        Member member = createMember(memberId, 10000L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        PointPolicy policy = PointPolicy.builder().signupPoint(0).build(); // 적립금 0
        given(pointPolicyRepository.findTopByOrderByUpdatedAtDesc()).willReturn(policy);

        PointEarnRequest request = new PointEarnRequest(memberId, PointEventType.EARN_SIGNUP, null, null);

        Long result = pointServiceImpl.earnPoint(request);

        assertThat(result).isEqualTo(10000L);
        then(pointHistoryRepository).should(never()).save(any(PointHistory.class));
    }

    /**
     * 포인트 조회 (Balance & History)
     */
    @Test
    @DisplayName("포인트 잔액 조회 성공 - 총 적립액이 null일 때 0으로 처리")
    void getBalance_Success_NullTotal() {
        Long memberId = 1L;
        Member member = createMember(memberId, 5000L);
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(pointHistoryRepository.sumEarnedPoints(memberId)).willReturn(null); // DB 리턴 null

        PointBalanceResponse response = pointServiceImpl.getBalance(memberId);

        assertThat(response.getCurrentPoint()).isEqualTo(5000L);
        assertThat(response.getTotalEarnedPoint()).isZero();
    }

    @Test
    @DisplayName("포인트 이력 조회 성공")
    void getHistory_Success() {
        Long memberId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        Member member = createMember(memberId, 10000L);
        PointHistory history = PointHistory.builder()
                .member(member)
                .amount(500L)
                .pointEventType(PointEventType.EARN_ORDER)
                .build();

        given(memberRepository.existsById(memberId)).willReturn(true);
        given(pointHistoryRepository.findAllByMemberId(memberId, pageable))
                .willReturn(new PageImpl<>(List.of(history)));

        Page<PointHistoryResponse> result = pointServiceImpl.getHistory(memberId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getAmount()).isEqualTo(500L);
    }

    /**
     * 관리자 기능
     */
    @Test
    @DisplayName("최신 정책 조회 및 수정 성공")
    void adminPolicy_Success() {
        // Get Policy
        PointPolicy policy = PointPolicy.builder().signupPoint(5000).build();
        given(pointPolicyRepository.findTopByOrderByUpdatedAtDesc()).willReturn(policy);

        PointAdminPolicyResponse response = pointServiceImpl.getRecentPolicy();
        assertThat(response.getSignupPoint()).isEqualTo(5000);

        // Update Policy
        PointAdminPolicyRequest request = new PointAdminPolicyRequest(100, 200, 300);
        pointServiceImpl.updatePolicy(request);
        then(pointPolicyRepository).should().save(any(PointPolicy.class));
    }

    @Test
    @DisplayName("관리자 수동 조정 성공 (지급)")
    void adjustment_Earn_Success() {
        Long memberId = 1L;
        Member member = createMember(memberId, 0L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        PointAdminAdjustmentRequest request = new PointAdminAdjustmentRequest(memberId, 1000L, "이벤트");
        pointServiceImpl.adjustmentMemberPoint(request);

        assertThat(member.getCurrentPoint()).isEqualTo(1000L);
        then(pointHistoryRepository).should().save(any(PointHistory.class));
    }

    /**
     * 반품 적립 회수 (deductPoint)
     */
    @Test
    @DisplayName("적립 포인트 회수(deduct) 성공 - 잔액이 음수가 될 수도 있음")
    void deductPoint_Success() {
        Long memberId = 1L;
        Member member = createMember(memberId, 500L); // 잔액 500원
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        // 1000원 회수 (잔액 -500원 예상)
        pointServiceImpl.deductPoint(memberId, 1000L, 200L);

        assertThat(member.getCurrentPoint()).isEqualTo(-500L);
        then(pointHistoryRepository).should().save(refEq(new PointHistory(
                200L, member, -1000L, "반품으로 인한 적립금 회수", PointEventType.EARN_CANCEL_RETURN, -500L, PointStatus.CONFIRMED
        ), "createdAt", "id"));
    }

    @Test
    @DisplayName("적립 포인트 회수 실패 - 회수 금액이 0 이하")
    void deductPoint_Fail_InvalidAmount() {
        Long memberId = 1L;
        Member member = createMember(memberId, 500L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> pointServiceImpl.deductPoint(memberId, 0L, 200L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    /**
     * TCC (Try-Confirm-Cancel) 로직 테스트
     */

    @Test
    @DisplayName("TCC 예약(reserve) 성공")
    void tcc_Reserve_Success() {
        Long memberId = 1L;
        Member member = createMember(memberId, 10000L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));
        given(pointHistoryRepository.existsByOrderIdAndPointEventType(100L, PointEventType.USE_ORDER))
                .willReturn(false);

        pointServiceImpl.reservePoint(memberId, 5000L, 100L);

        assertThat(member.getCurrentPoint()).isEqualTo(5000L); // 10000 - 5000

        String expectedDescription = String.format("%s (주문번호: %d)",
                PointEventType.USE_ORDER.getDescription(), 100L);

        then(pointHistoryRepository).should().save(refEq(new PointHistory(
                100L,
                member,
                -5000L,
                expectedDescription, // "상품 결제 사용 (주문번호: 100)"
                PointEventType.USE_ORDER,
                5000L,
                PointStatus.RESERVED
        ), "createdAt", "id"));
    }

    @Test
    @DisplayName("TCC 예약 중복 요청 시 무시")
    void tcc_Reserve_Ignore_Duplicate() {
        // 이미 해당 주문번호로 USE_ORDER 내역이 존재
        given(pointHistoryRepository.existsByOrderIdAndPointEventType(100L, PointEventType.USE_ORDER))
                .willReturn(true);

        pointServiceImpl.reservePoint(1L, 5000L, 100L);

        // 아무 일도 일어나지 않음 (멤버 조회 x, 저장 x)
        then(memberRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("TCC 확정(confirm) 성공")
    void tcc_Confirm_Success() {
        PointHistory history = PointHistory.builder()
                .orderId(100L)
                .pointEventType(PointEventType.USE_ORDER)
                .status(PointStatus.RESERVED) // 예약 상태
                .build();

        given(pointHistoryRepository.findByOrderIdAndPointEventType(100L, PointEventType.USE_ORDER))
                .willReturn(Optional.of(history));

        pointServiceImpl.confirmPoint(1L, 5000L, 100L);

        assertThat(history.getStatus()).isEqualTo(PointStatus.CONFIRMED); // 상태 변경 확인
    }

    @Test
    @DisplayName("TCC 확정 실패 - 내역 없음")
    void tcc_Confirm_Fail_NotFound() {
        given(pointHistoryRepository.findByOrderIdAndPointEventType(anyLong(), any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> pointServiceImpl.confirmPoint(1L, 5000L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.POINT_NOT_FOUND);
    }

    @Test
    @DisplayName("TCC 확정 무시 - 이미 확정된 경우")
    void tcc_Confirm_Ignore_AlreadyConfirmed() {
        PointHistory history = PointHistory.builder()
                .status(PointStatus.CONFIRMED)
                .build();
        given(pointHistoryRepository.findByOrderIdAndPointEventType(100L, PointEventType.USE_ORDER))
                .willReturn(Optional.of(history));

        pointServiceImpl.confirmPoint(1L, 5000L, 100L);

        // 상태 변경 로직이 다시 실행되지 않았음을 검증 (간접적으로는 상태가 여전히 CONFIRMED)
        assertThat(history.getStatus()).isEqualTo(PointStatus.CONFIRMED);
    }

    @Test
    @DisplayName("TCC 취소(cancel) 성공 - 예약 상태일 때")
    void tcc_Cancel_Success() {
        Long memberId = 1L;
        Member member = createMember(memberId, 5000L); // 잔액 5000 (예약으로 차감된 상태)

        PointHistory history = PointHistory.builder()
                .orderId(100L)
                .member(member)
                .amount(-5000L)
                .status(PointStatus.RESERVED)
                .build();

        // 1. History 조회
        given(pointHistoryRepository.findByOrderIdAndPointEventType(100L, PointEventType.USE_ORDER))
                .willReturn(Optional.of(history));
        // 2. Revert 내부에서 멤버 조회
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        pointServiceImpl.cancelPoint(memberId, 5000L, 100L);

        // 1. 기존 내역 상태 CANCELED 변경
        assertThat(history.getStatus()).isEqualTo(PointStatus.CANCELED);
        // 2. 환불 로직 실행으로 잔액 10000원으로 복구
        assertThat(member.getCurrentPoint()).isEqualTo(10000L);
        // 3. 환불 내역 저장 (USE_CANCEL_ORDER)
        then(pointHistoryRepository).should().save(any(PointHistory.class));
    }

    @Test
    @DisplayName("TCC 취소 무시 - 이미 취소된 경우")
    void tcc_Cancel_Ignore_AlreadyCanceled() {
        PointHistory history = PointHistory.builder()
                .status(PointStatus.CANCELED)
                .build();
        given(pointHistoryRepository.findByOrderIdAndPointEventType(100L, PointEventType.USE_ORDER))
                .willReturn(Optional.of(history));

        pointServiceImpl.cancelPoint(1L, 5000L, 100L);

        then(memberRepository).shouldHaveNoInteractions(); // 환불 로직 안탐
    }

    // Helper
    private Member createMember(Long id, Long point) {
        Grade grade = Grade.builder()
                .gradeName("GENERAL")
                .pointRate(BigDecimal.valueOf(0.01))
                .build();

        return Member.builder()
                .id(id)
                .loginId("test" + id)
                .currentPoint(point)
                .grade(grade)
                .build();
    }
}