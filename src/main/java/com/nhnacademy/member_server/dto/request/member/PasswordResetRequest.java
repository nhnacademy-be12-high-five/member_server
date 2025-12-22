package com.nhnacademy.member_server.dto.request.member;

import lombok.Getter;

@Getter
public class PasswordResetRequest {
    String loginId;
    String email;
    String authCode;
    String newPassword;
}
