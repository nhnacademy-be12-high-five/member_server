package com.nhnacademy.member_server.dto;

import java.util.List;

public record CartListResponse(
        List<CartDetailResponse> items,
        long totalCartPrice // 전체 총 주문 금액
) {}