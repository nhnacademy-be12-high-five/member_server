package com.nhnacademy.member_server.dto.request.member;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequest {
    String loginId;
    String email;
    String authCode;
    String newPassword;
}
