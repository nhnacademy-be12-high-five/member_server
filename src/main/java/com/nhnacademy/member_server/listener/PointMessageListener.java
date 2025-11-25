package com.nhnacademy.member_server.listener;

import com.nhnacademy.member_server.dto.request.PointEarnRequest;
import com.nhnacademy.member_server.service.impl.PointServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class PointMessageListener {
    private final PointServiceImpl pointServiceImpl;

    @RabbitListener(queues = "point-queue")
    public void receiveMessage(PointEarnRequest requestDto){
        log.info("RabbitMQ 메시지 수신: memberId={}, amount={}", requestDto.getMemberId(), requestDto.getPureAmount());

        try {
            pointServiceImpl.earnPoint(requestDto);
            log.info("포인트 적립 완료");
        } catch (Exception e) {
            log.error("포인트 적립 실패", e);
            // (실패 시 처리 로직 추가?)
        }
    }
}
