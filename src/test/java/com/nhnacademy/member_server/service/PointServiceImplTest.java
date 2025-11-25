package com.nhnacademy.member_server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhnacademy.member_server.dto.request.PointTransactionRequest;
import com.nhnacademy.member_server.entity.Member;
import com.nhnacademy.member_server.entity.PointHistory;
import com.nhnacademy.member_server.exception.BusinessException;
import com.nhnacademy.member_server.exception.ErrorCode;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.repository.PointHistoryRepository;
import com.nhnacademy.member_server.service.impl.PointServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PointServiceImplTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @InjectMocks
    private PointServiceImpl pointServiceImpl;

    @Test
    @DisplayName("포인트 사용 성공 테스트")
    void usePoints_Success() {
        // given
        Long memberId = 1L;
        Long currentPoint = 10000L;
        Long useAmount = 5000L;

        Member member = new Member();
        member.setCurrentPoint(currentPoint);

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

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
        Member member = new Member();
        member.setCurrentPoint(1000L);

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

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
        Member member = new Member();
        member.setCurrentPoint(5000L);

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        PointTransactionRequest request = new PointTransactionRequest(memberId, 1000L, 1L);

        // when
        Long result = pointServiceImpl.revertPoint(request);

        // then
        assertThat(result).isEqualTo(6000L); // 5000 + 1000
    }
}