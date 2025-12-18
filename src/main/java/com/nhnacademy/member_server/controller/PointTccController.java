package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.service.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
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
}