package com.nhnacademy.member_server.controller;

import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.member_server.dto.request.PointTransactionRequest;
import com.nhnacademy.member_server.dto.response.PointBalanceResponse;
import com.nhnacademy.member_server.service.PointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PointTccController.class)
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
    @DisplayName("반품 시 적립 포인트 회수 (deductPoint) 성공")
    void deductPoint_Success() throws Exception {
        Long memberId = 1L;
        Integer amount = 500;
        Long orderId = 200L;

        mockMvc.perform(post("/api/members/{memberId}/point-deduct", memberId)
                        .param("amount", String.valueOf(amount))
                        .param("orderId", String.valueOf(orderId)))
                .andDo(print())
                .andExpect(status().isOk());

        then(pointService).should().deductPoint(memberId, (long) amount, orderId);
    }

    @Test
    @DisplayName("반품 시 사용 포인트 환불 (revertPoint) 성공")
    void revertPoint_Success() throws Exception {
        Long memberId = 1L;
        Integer amount = 1000;
        Long orderId = 200L;

        PointTransactionRequest expectedRequest = new PointTransactionRequest(
                memberId,
                (long) amount,
                orderId
        );

        mockMvc.perform(post("/api/members/{memberId}/point/revert", memberId)
                        .param("amount", String.valueOf(amount))
                        .param("orderId", String.valueOf(orderId)))
                .andDo(print())
                .andExpect(status().isOk());

        then(pointService).should().revertUsePointForReturn(refEq(expectedRequest));
    }
}