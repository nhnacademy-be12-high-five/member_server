package com.nhnacademy.member_server.dto;

import com.nhnacademy.member_server.entity.PointEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PointEarnRequest { //
    @Schema(description = "포인트 적립 될 유저 아이디", example = "1")
    private Long memberId;

    @Schema(description = "주문 금액 (타입 ORDER 일 때만 넣기)", example = "20000")
    private Long orderAmount;

    @Schema(description = "적립 사유", example = "ORDER / REVIEW / PHOTO_REVIEW / SIGNUP")
    private PointEventType eventType;
}