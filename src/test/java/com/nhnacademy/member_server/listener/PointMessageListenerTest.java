package com.nhnacademy.member_server.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nhnacademy.member_server.dto.request.PointEarnRequest;
import com.nhnacademy.member_server.entity.PointEventType;
import com.nhnacademy.member_server.service.PointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PointMessageListenerTest {

    @Mock
    private PointService pointService;

    @InjectMocks
    private PointMessageListener pointMessageListener;

    @Test
    @DisplayName("메시지 수신 성공")
    void receiveMessage_Success() {
        PointEarnRequest request = new PointEarnRequest(1L, PointEventType.EARN_ORDER, 5000L, 100L);
        pointMessageListener.receiveMessage(request);
        verify(pointService, times(1)).earnPoint(request);
    }

    @Test
    @DisplayName("메시지 수신 실패: 서비스 에러 + 리스너는 돌아감")
    void receiveMessage_Exception() {
        PointEarnRequest request = new PointEarnRequest(1L, PointEventType.EARN_ORDER, 5000L, 100L);

        doThrow(new RuntimeException("DB 연결 오류")).when(pointService).earnPoint(any());

        pointMessageListener.receiveMessage(request);

        verify(pointService, times(1)).earnPoint(request);
    }
}