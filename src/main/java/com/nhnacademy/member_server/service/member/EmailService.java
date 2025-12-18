package com.nhnacademy.member_server.service.member;

public interface EmailService {
    void sendVerificationCode(String email);

    boolean verifyCode(String email, String inputCode);
}