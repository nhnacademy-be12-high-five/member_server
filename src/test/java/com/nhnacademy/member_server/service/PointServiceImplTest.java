package com.nhnacademy.member_server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.nhnacademy.member_server.dto.request.PointAdminAdjustmentRequest;
import com.nhnacademy.member_server.dto.request.PointAdminPolicyRequest;
import com.nhnacademy.member_server.dto.request.PointTransactionCreateRequest;
import com.nhnacademy.member_server.entity.PointEventType;
import com.nhnacademy.member_server.entity.PointHistory;
import com.nhnacademy.member_server.entity.PointPolicy;
import com.nhnacademy.member_server.entity.PointStatus;
import com.nhnacademy.member_server.entity.member.Grade;
import com.nhnacademy.member_server.entity.member.Member;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.repository.PointHistoryRepository;
import com.nhnacademy.member_server.repository.PointPolicyRepository;
import com.nhnacademy.member_server.service.impl.PointServiceImpl;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    // --- 1. 통합 트랜잭션 (createTransaction) 테스트 ---

    @Test
    @DisplayName("통합: 포인트 적립(EARN_ORDER) 성공")
    void createTransaction_EarnOrder_Success() {
        // given
        Long memberId = 1L;
        Member member = createMember(memberId, 10000L);
        // 등급 적립률 1% 가정 (Member 생성 메서드 참고)
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        PointTransactionCreateRequest request = PointTransactionCreateRequest.builder()
                .memberId(memberId)
                .pointEventType(PointEventType.EARN_ORDER)
                .amount(50000L) // 50000 * 0.01 = 500원 적립 예상
                .orderId(100L)
                .build();

        // when
        Long result = pointServiceImpl.createTransaction(request);

        // then
        assertThat(result).isEqualTo(10500L); // 10000 + 500

        ArgumentCaptor<PointHistory> captor = ArgumentCaptor.forClass(PointHistory.class);
        then(pointHistoryRepository).should().save(captor.capture());

        PointHistory saved = captor.getValue();
        assertThat(saved.getAmount()).isEqualTo(500L);
        assertThat(saved.getPointEventType()).isEqualTo(PointEventType.EARN_ORDER);
        assertThat(saved.getStatus()).isEqualTo(PointStatus.CONFIRMED);
    }

    @Test
    @DisplayName("통합: 포인트 사용(USE_ORDER) 성공")
    void createTransaction_UseOrder_Success() {
        // given
        Long memberId = 1L;
        Member member = createMember(memberId, 10000L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        PointTransactionCreateRequest request = PointTransactionCreateRequest.builder()
                .memberId(memberId)
                .pointEventType(PointEventType.USE_ORDER)
                .amount(5000L)
                .orderId(200L)
                .build();

        // when
        Long result = pointServiceImpl.createTransaction(request);

        // then
        assertThat(result).isEqualTo(5000L); // 10000 - 5000
        assertThat(member.getCurrentPoint()).isEqualTo(5000L);

        ArgumentCaptor<PointHistory> captor = ArgumentCaptor.forClass(PointHistory.class);
        then(pointHistoryRepository).should().save(captor.capture());

        // 사용은 음수로 저장됨
        assertThat(captor.getValue().getAmount()).isEqualTo(-5000L);
        assertThat(captor.getValue().getPointEventType()).isEqualTo(PointEventType.USE_ORDER);
    }

    @Test
    @DisplayName("통합: 사용 취소/환불(USE_CANCEL_ORDER) 성공")
    void createTransaction_Refund_Success() {
        // given
        Long memberId = 1L;
        Member member = createMember(memberId, 5000L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        PointTransactionCreateRequest request = PointTransactionCreateRequest.builder()
                .memberId(memberId)
                .pointEventType(PointEventType.USE_CANCEL_ORDER)
                .amount(1000L)
                .orderId(200L)
                .build();

        // when
        Long result = pointServiceImpl.createTransaction(request);

        // then
        assertThat(result).isEqualTo(6000L); // 5000 + 1000

        ArgumentCaptor<PointHistory> captor = ArgumentCaptor.forClass(PointHistory.class);
        then(pointHistoryRepository).should().save(captor.capture());

        assertThat(captor.getValue().getAmount()).isEqualTo(1000L);
        assertThat(captor.getValue().getPointEventType()).isEqualTo(PointEventType.USE_CANCEL_ORDER);
    }

    @Test
    @DisplayName("통합: 반품 시 적립금 회수(EARN_CANCEL_RETURN) 성공")
    void createTransaction_Deduct_Success() {
        // given
        Long memberId = 1L;
        Member member = createMember(memberId, 1000L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        PointTransactionCreateRequest request = PointTransactionCreateRequest.builder()
                .memberId(memberId)
                .pointEventType(PointEventType.EARN_CANCEL_RETURN)
                .amount(500L)
                .orderId(300L)
                .build();

        // when
        Long result = pointServiceImpl.createTransaction(request);

        // then
        assertThat(result).isEqualTo(500L); // 1000 - 500

        ArgumentCaptor<PointHistory> captor = ArgumentCaptor.forClass(PointHistory.class);
        then(pointHistoryRepository).should().save(captor.capture());

        // 회수는 음수로 저장
        assertThat(captor.getValue().getAmount()).isEqualTo(-500L);
        assertThat(captor.getValue().getPointEventType()).isEqualTo(PointEventType.EARN_CANCEL_RETURN);
    }

    @Test
    @DisplayName("통합: 정책 기반 적립(EARN_REVIEW) 성공")
    void createTransaction_EarnReview_Success() {
        // given
        Long memberId = 1L;
        Member member = createMember(memberId, 10000L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        PointPolicy policy = PointPolicy.builder().reviewPoint(200).build();
        given(pointPolicyRepository.findTopByOrderByUpdatedAtDesc()).willReturn(policy);

        PointTransactionCreateRequest request = PointTransactionCreateRequest.builder()
                .memberId(memberId)
                .pointEventType(PointEventType.EARN_REVIEW)
                // amount 없음 -> 정책에서 가져옴
                .build();

        // when
        Long result = pointServiceImpl.createTransaction(request);

        // then
        assertThat(result).isEqualTo(10200L);
    }

    @Test
    @DisplayName("통합: 환불 재적립(EARN_REFUND) - 중복 요청 시 무시")
    void createTransaction_EarnRefund_Duplicate_Ignore() {
        // given
        Long memberId = 1L;
        Member member = createMember(memberId, 10000L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        // 이미 해당 주문건으로 환불 적립 내역이 존재함
        given(pointHistoryRepository.existsByOrderIdAndPointEventType(100L, PointEventType.EARN_REFUND))
                .willReturn(true);

        PointTransactionCreateRequest request = PointTransactionCreateRequest.builder()
                .memberId(memberId)
                .pointEventType(PointEventType.EARN_REFUND)
                .amount(500L)
                .orderId(100L)
                .build();

        // when
        Long result = pointServiceImpl.createTransaction(request);

        // then
        assertThat(result).isEqualTo(10000L); // 변화 없음
        then(pointHistoryRepository).should(never()).save(any(PointHistory.class));
    }


    // --- 2. TCC 관련 테스트 ---

    @Test
    @DisplayName("TCC 예약(Reserve) 성공 - 포인트 차감 및 상태 RESERVED")
    void tcc_Reserve_Success() {
        // given
        Long memberId = 1L;
        Member member = createMember(memberId, 10000L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));
        given(pointHistoryRepository.existsByOrderIdAndPointEventType(100L, PointEventType.USE_ORDER))
                .willReturn(false);

        // when
        pointServiceImpl.reservePoint(memberId, 5000L, 100L);

        // then
        assertThat(member.getCurrentPoint()).isEqualTo(5000L); // 10000 - 5000 (선차감)

        ArgumentCaptor<PointHistory> captor = ArgumentCaptor.forClass(PointHistory.class);
        then(pointHistoryRepository).should().save(captor.capture());

        PointHistory saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(PointStatus.RESERVED);
        assertThat(saved.getPointEventType()).isEqualTo(PointEventType.USE_ORDER);
        assertThat(saved.getAmount()).isEqualTo(-5000L);
    }

    @Test
    @DisplayName("TCC 확정(Confirm) 성공")
    void tcc_Confirm_Success() {
        // given
        PointHistory history = PointHistory.builder()
                .orderId(100L)
                .status(PointStatus.RESERVED)
                .pointEventType(PointEventType.USE_ORDER)
                .build();

        given(pointHistoryRepository.findByOrderIdAndPointEventType(100L, PointEventType.USE_ORDER))
                .willReturn(Optional.of(history));

        // when
        pointServiceImpl.confirmPoint(1L, 5000L, 100L);

        // then
        assertThat(history.getStatus()).isEqualTo(PointStatus.CONFIRMED);
    }

    @Test
    @DisplayName("TCC 취소(Cancel) 성공 - 상태 취소 후 createTransaction으로 환불")
    void tcc_Cancel_Success() {
        // given
        Long memberId = 1L;
        Member member = createMember(memberId, 5000L); // 이미 차감된 상태

        // 취소할 기존 예약 내역
        PointHistory history = PointHistory.builder()
                .orderId(100L)
                .status(PointStatus.RESERVED)
                .pointEventType(PointEventType.USE_ORDER)
                .build();

        given(pointHistoryRepository.findByOrderIdAndPointEventType(100L, PointEventType.USE_ORDER))
                .willReturn(Optional.of(history));

        // createTransaction 내부에서 호출됨
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        // when
        pointServiceImpl.cancelPoint(memberId, 5000L, 100L);

        // then
        // 1. 기존 내역 상태 변경 확인
        assertThat(history.getStatus()).isEqualTo(PointStatus.CANCELED);

        // 2. 환불 트랜잭션(USE_CANCEL_ORDER)이 저장되었는지 확인
        ArgumentCaptor<PointHistory> captor = ArgumentCaptor.forClass(PointHistory.class);
        // TCC 취소 시: cancelPoint 내부에서 createTransaction 호출 -> save 발생
        // 주의: reserve 때 save 1번(Mock 아님 실제면), 여기서는 cancelPoint 메서드만 테스트하므로 save는 1번 호출됨
        then(pointHistoryRepository).should().save(captor.capture());

        PointHistory refundHistory = captor.getValue();
        assertThat(refundHistory.getPointEventType()).isEqualTo(PointEventType.USE_CANCEL_ORDER);
        assertThat(refundHistory.getAmount()).isEqualTo(5000L);
        assertThat(refundHistory.getOriginalPointHistoryId()).isEqualTo(999L);

        // 3. 멤버 잔액 복구 확인
        assertThat(member.getCurrentPoint()).isEqualTo(10000L);
    }

    // --- 3. 관리자 및 기타 테스트 ---

    @Test
    @DisplayName("관리자: 포인트 조정(지급) 성공")
    void admin_Adjustment_Earn_Success() {
        // given
        Long memberId = 1L;
        Member member = createMember(memberId, 0L);
        given(memberRepository.findByIdForUpdate(memberId)).willReturn(Optional.of(member));

        PointAdminAdjustmentRequest request = new PointAdminAdjustmentRequest(memberId, 1000L, "이벤트 지급");

        // when
        pointServiceImpl.adjustmentMemberPoint(request);

        // then
        assertThat(member.getCurrentPoint()).isEqualTo(1000L);

        ArgumentCaptor<PointHistory> captor = ArgumentCaptor.forClass(PointHistory.class);
        then(pointHistoryRepository).should().save(captor.capture());

        assertThat(captor.getValue().getPointEventType()).isEqualTo(PointEventType.EARN_ADMIN);
    }

    @Test
    @DisplayName("정책 수정 테스트")
    void updatePolicy_Success() {
        // given
        PointAdminPolicyRequest request = new PointAdminPolicyRequest(1000, 200, 500);

        // when
        pointServiceImpl.updatePolicy(request);

        // then
        then(pointPolicyRepository).should().save(any(PointPolicy.class));
    }

    // --- Helper Method ---
    private Member createMember(Long id, Long point) {
        Grade grade = Grade.builder()
                .gradeName("GENERAL")
                .pointRate(BigDecimal.valueOf(0.01)) // 1%
                .build();

        return Member.builder()
                .id(id)
                .loginId("test" + id)
                .currentPoint(point)
                .grade(grade)
                .build();
    }
}