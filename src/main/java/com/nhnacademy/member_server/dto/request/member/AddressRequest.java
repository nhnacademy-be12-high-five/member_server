package com.nhnacademy.member_server.dto.request.member;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class AddressRequest {
    @NotBlank(message = "별칭은 필수입니다")
    private String alias;
    @NotBlank(message = "도로명 주소는 필수입니다")
    private String roadAddress;
    @NotBlank(message = "상세 주소는 필수입니다")
    private String detailAddress;
}
