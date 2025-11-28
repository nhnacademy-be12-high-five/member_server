package com.nhnacademy.member_server.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointAdminPolicyResponse {
    @Schema(description = "회원가입 적립금", example = "5000")
    private Integer signupPoint;

    @Schema(description = "일반 리뷰 적립금", example = "200")
    private Integer reviewPoint;

    @Schema(description = "포토 리뷰 적립금", example = "500")
    private Integer photoPoint;
}