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

    @PostMapping("/{userId}/point/reserve")
    public void reservePoint(@PathVariable("userId") Long userId, @RequestParam("amount") Long amount, @RequestParam("orderId") Long orderId) {
        pointService.reservePoint(userId, amount, orderId);
    }

    @PostMapping("/{userId}/point/confirm")
    public void confirmPoint(@PathVariable("userId") Long userId, @RequestParam("amount") Long amount, @RequestParam("orderId") Long orderId) {
        pointService.confirmPoint(userId, amount, orderId);
    }

    @PostMapping("/{userId}/point/cancel")
    public void cancelPoint(@PathVariable("userId") Long userId, @RequestParam("amount") Long amount, @RequestParam("orderId") Long orderId) {
        pointService.cancelPoint(userId, amount, orderId);
    }

    // MemberClient.getPointBalance
    @GetMapping("/{userId}/point-balance")
    public Integer getPointBalance(@PathVariable("userId") Long userId) {
        return pointService.getBalance(userId).getCurrentPoint().intValue();
    }
}