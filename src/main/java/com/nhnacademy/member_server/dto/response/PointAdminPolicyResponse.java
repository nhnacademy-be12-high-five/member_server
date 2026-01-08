package com.nhnacademy.member_server.dto.response;

import com.nhnacademy.member_server.entity.PointPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    @Schema(description = "정책 업데이트된 시간 (현재시각)")
    private LocalDateTime updatedAt;

    public static PointAdminPolicyResponse from(PointPolicy entity) {
        return PointAdminPolicyResponse.builder()
                .signupPoint(entity.getSignupPoint())
                .reviewPoint(entity.getReviewPoint())
                .photoPoint(entity.getPhotoPoint())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

}