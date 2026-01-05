package com.nhnacademy.member_server.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.member_server.dto.request.PointTransactionCreateRequest;
import com.nhnacademy.member_server.dto.response.PointBalanceResponse;
import com.nhnacademy.member_server.entity.PointEventType;
import com.nhnacademy.member_server.service.PointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PointController.class)
@AutoConfigureMockMvc(addFilters = false)
class PointTccControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PointService pointService;

    @Test
    @DisplayName("포인트 적립 예약 (reservePoint) 성공")
    void reservePoint_Success() throws Exception {
        Long memberId = 1L;
        Long amount = 1000L;
        Long orderId = 100L;

        mockMvc.perform(post("/api/members/{memberId}/point/reserve", memberId)
                        .param("amount", String.valueOf(amount))
                        .param("orderId", String.valueOf(orderId)))
                .andDo(print())
                .andExpect(status().isOk());

        then(pointService).should().reservePoint(memberId, amount, orderId);
    }

    @Test
    @DisplayName("포인트 적립 확정 (confirmPoint) 성공")
    void confirmPoint_Success() throws Exception {
        Long memberId = 1L;
        Long amount = 1000L;
        Long orderId = 100L;

        mockMvc.perform(post("/api/members/{memberId}/point/confirm", memberId)
                        .param("amount", String.valueOf(amount))
                        .param("orderId", String.valueOf(orderId)))
                .andDo(print())
                .andExpect(status().isOk());

        then(pointService).should().confirmPoint(memberId, amount, orderId);
    }

    @Test
    @DisplayName("포인트 사용 취소 (cancelPoint) 성공")
    void cancelPoint_Success() throws Exception {
        Long memberId = 1L;
        Long amount = 500L;
        Long orderId = 100L;

        mockMvc.perform(post("/api/members/{memberId}/point/cancel", memberId)
                        .param("amount", String.valueOf(amount))
                        .param("orderId", String.valueOf(orderId)))
                .andDo(print())
                .andExpect(status().isOk());

        then(pointService).should().cancelPoint(memberId, amount, orderId);
    }

    @Test
    @DisplayName("포인트 잔액 조회 (getPointBalance) 성공")
    void getPointBalance_Success() throws Exception {
        Long memberId = 1L;
        PointBalanceResponse balanceResponse = new PointBalanceResponse(memberId, 2500L, 10000L);

        given(pointService.getBalance(memberId)).willReturn(balanceResponse);

        mockMvc.perform(get("/api/members/{memberId}/point-balance", memberId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("2500"));
        then(pointService).should().getBalance(memberId);
    }

    @Test
    @DisplayName("반품 시 적립 포인트 회수 (deductPoint) -> createTransaction 호출 검증")
    void deductPoint_Success() throws Exception {
        Long memberId = 1L;
        Long amount = 500L;
        Long orderId = 200L;

        // Service는 통합 메서드인 createTransaction을 호출하게 됨
        given(pointService.createTransaction(any(PointTransactionCreateRequest.class)))
                .willReturn(amount);

        mockMvc.perform(post("/api/members/{memberId}/point-deduct", memberId)
                        .param("amount", String.valueOf(amount))
                        .param("orderId", String.valueOf(orderId)))
                .andDo(print())
                .andExpect(status().isOk());

        // 검증: createTransaction이 호출되었는지, 그리고 EventType이 올바른지 확인
        ArgumentCaptor<PointTransactionCreateRequest> captor = ArgumentCaptor.forClass(PointTransactionCreateRequest.class);
        then(pointService).should().createTransaction(captor.capture());

        PointTransactionCreateRequest request = captor.getValue();
        assertThat(request.getMemberId()).isEqualTo(memberId);
        assertThat(request.getAmount()).isEqualTo(amount);
        assertThat(request.getOrderId()).isEqualTo(orderId);
        // ★ 핵심: 컨트롤러가 내부적으로 EARN_CANCEL_RETURN 타입으로 요청을 만들었는지 확인
        assertThat(request.getPointEventType()).isEqualTo(PointEventType.EARN_CANCEL_RETURN);
    }

    @Test
    @DisplayName("반품 시 사용 포인트 환불 (revertPoint) -> createTransaction 호출 검증")
    void revertPoint_Success() throws Exception {
        Long memberId = 1L;
        Long amount = 1000L;
        Long orderId = 200L;

        given(pointService.createTransaction(any(PointTransactionCreateRequest.class)))
                .willReturn(6000L); // 예상 잔액 리턴

        mockMvc.perform(post("/api/members/{memberId}/point/revert", memberId)
                        .param("amount", String.valueOf(amount))
                        .param("orderId", String.valueOf(orderId)))
                .andDo(print())
                .andExpect(status().isOk());

        // 검증
        ArgumentCaptor<PointTransactionCreateRequest> captor = ArgumentCaptor.forClass(PointTransactionCreateRequest.class);
        then(pointService).should().createTransaction(captor.capture());

        PointTransactionCreateRequest request = captor.getValue();
        assertThat(request.getMemberId()).isEqualTo(memberId);
        assertThat(request.getAmount()).isEqualTo(amount);
        assertThat(request.getOrderId()).isEqualTo(orderId);
        // ★ 핵심: 컨트롤러가 내부적으로 USE_CANCEL_RETURN (또는 USE_CANCEL_ORDER) 타입으로 요청했는지 확인
        // 문맥상 반품(Revert for Return)이므로 USE_CANCEL_RETURN을 기대
        assertThat(request.getPointEventType()).isIn(PointEventType.USE_CANCEL_RETURN, PointEventType.USE_CANCEL_ORDER);
    }
}