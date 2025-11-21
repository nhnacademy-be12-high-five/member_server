package com.nhnacademy.member_server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PointTransactionRequest {
    @Schema(description = "포인트 적립/사용/환불 될 유저 아이디", example = "1")
    private Long memberId;
    @Schema(description = "포인트 적립/사용/환불 액수", example = "100")
    private Long amount;
    @Schema(description = "포인트 적립/사용/환불 사유", example = "도서 구매: 배송 완료 포인트 적립")
    private String description;
}
