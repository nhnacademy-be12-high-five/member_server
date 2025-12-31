package com.nhnacademy.member_server.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@FeignClient(name = "TEAM5-ORDER-SERVER")
public interface OrderFeignClient {

    @PostMapping("/api/internal/orders/users/bulk-total-amount")
    ResponseEntity<Map<Long, Long>> getBulkTotalAmounts(
            @RequestBody List<Long> userIds,
            @RequestParam("since") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since
    );
}