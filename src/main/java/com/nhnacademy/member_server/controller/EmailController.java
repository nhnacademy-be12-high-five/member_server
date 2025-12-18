package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.dto.request.member.EmailVerifyRequest;
import com.nhnacademy.member_server.service.member.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/email")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService; // 인터페이스 주입

    // 1. 인증번호 전송 요청
    @PostMapping("/send")
    public ResponseEntity<Void> sendEmail(@RequestParam String email) {
        emailService.sendVerificationCode(email);
        return ResponseEntity.ok().build();
    }

    // 2. 인증번호 확인 요청
    @PostMapping("/verify")
    public ResponseEntity<String> verifyEmail(@RequestBody EmailVerifyRequest request) {
        boolean isVerified = emailService.verifyCode(request.getEmail(), request.getCode());
        
        if (isVerified) {
            return ResponseEntity.ok("인증 성공");
        } else {
            return ResponseEntity.status(400).body("인증 실패: 코드가 다르거나 만료되었습니다.");
        }
    }
}