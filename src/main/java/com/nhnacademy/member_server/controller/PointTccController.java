package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.dto.request.PointAdminAdjustmentRequest;
import com.nhnacademy.member_server.dto.request.PointTransactionRequest;
import com.nhnacademy.member_server.service.PointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
@Slf4j
public class PointTccController {

    private final PointService pointService;

    @PostMapping("/{memberId}/point/reserve")
    public void reservePoint(@PathVariable("memberId") Long memberId, @RequestParam("amount") Long amount, @RequestParam("orderId") Long orderId) {
        pointService.reservePoint(memberId, amount, orderId);
    }

    @PostMapping("/{memberId}/point/confirm")
    public void confirmPoint(@PathVariable("memberId") Long memberId, @RequestParam("amount") Long amount, @RequestParam("orderId") Long orderId) {
        pointService.confirmPoint(memberId, amount, orderId);
    }

    @PostMapping("/{memberId}/point/cancel")
    public void cancelPoint(@PathVariable("memberId") Long memberId, @RequestParam("amount") Long amount, @RequestParam("orderId") Long orderId) {
        pointService.cancelPoint(memberId, amount, orderId);
    }

    // MemberClient.getPointBalance
    @GetMapping("/{memberId}/point-balance")
    public Integer getPointBalance(@PathVariable("memberId") Long memberId) {
        return pointService.getBalance(memberId).getCurrentPoint().intValue();
    }



    // 적립 포인트 회수 (반품 시 구매 확정으로 받은 포인트를 뺏음)
    @PostMapping("/{memberId}/point-deduct")
    public void deductPoint(@PathVariable("memberId") Long memberId,
                            @RequestParam("amount") Integer amount) {

        log.info("반품 적립 회수 요청: memberId={}, amount={}", memberId, amount);

        // amount가 양수로 들어오므로 음수로 변환하여 차감 요청
        // PointServiceImpl.adjustmentMemberPoint 가 음수면 USE_ADMIN으로 처리함
        PointAdminAdjustmentRequest request = new PointAdminAdjustmentRequest(
                memberId,
                (long) -amount,
                "반품으로 인한 적립 취소" // 이력에 남을 사유
        );
        pointService.adjustmentMemberPoint(request);
    }


    // 기존 cancelPoint는 'CONFIRMED' 상태인 주문을 못 건드리므로, 강제 환불(revert)이 필요함 (기존 메서드 활용)
    // 확정된 주문에 사용했던 포인트 환불
    @PostMapping("/{memberId}/point/revert")
    public void revertPoint(@PathVariable("memberId") Long memberId,
                            @RequestParam("amount") Integer amount,
                            @RequestParam("orderId") Long orderId) {

        log.info("반품 포인트 환불 요청: memberId={}, amount={}, orderId={}", memberId, amount, orderId);

        PointTransactionRequest request = new PointTransactionRequest(
                memberId,
                (long) amount,
                orderId
        );
        pointService.revertPoint(request);
    }
}