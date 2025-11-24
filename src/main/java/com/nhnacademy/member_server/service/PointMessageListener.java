package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.dto.PointTransactionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointMessageListener {
    private final PointService pointService;

    @RabbitListener(queues = "point-queue")
    public void receiveMessage(PointTransactionRequest requestDto){
        log.info("RabbitMQ 메시지 수신: userId={}, amount={}", requestDto.getMemberId(), requestDto.getAmount());

        try {
            pointService.earnPoint(requestDto);
            log.info("포인트 적립 완료");
        } catch (Exception e) {
            log.error("포인트 적립 실패", e);
            // (실패 시 처리 로직 추가?)
        }
    }
}
