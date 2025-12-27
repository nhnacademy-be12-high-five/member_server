package com.nhnacademy.member_server.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

@FeignClient(name = "TEAM5-ORDER-SERVER")
public interface OrderFeignClient {

    @GetMapping("/api/internal/orders/users/{userId}/total-amount")
    ResponseEntity<Long> getTotalAmount(
            @PathVariable("userId") Long userId,
            @RequestParam("since") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since
    );
}