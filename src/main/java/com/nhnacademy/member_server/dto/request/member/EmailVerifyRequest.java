package com.nhnacademy.member_server.dto.request.member;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EmailVerifyRequest {
    private String email; // 프론트에서 보낸 이메일
    private String code;  // 사용자가 입력한 인증번호 6자리
}