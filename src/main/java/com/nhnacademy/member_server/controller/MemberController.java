package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.service.impl.AuthServiceImpl;
import com.nhnacademy.member_server.service.impl.MemberServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberServiceImpl memberServiceImpl; // 회원 상태 관리
    private final AuthServiceImpl authServiceImpl;

//    @GetMapping
//    public ResponseEntity<>

    @GetMapping("/my-page")
    public ResponseEntity<String> getMyPage(
            @RequestHeader("X-User-ID") Long userId,
            @RequestHeader("X-role") String role
    ) {
        return ResponseEntity.ok("마이페이지 접근 성공! 당신의 회원 ID는: " + userId + "role : " + role);
    }

    @DeleteMapping("/withdraw")
    public ResponseEntity<Void> withdraw(@RequestHeader(name = "X-USER-ID") long loginId) {
        memberServiceImpl.withdraw(loginId);
        authServiceImpl.logout(loginId);
        ResponseCookie deleteCookie = ResponseCookie.from("refresh-token", "")
                .path("/")
                .httpOnly(true)
                .secure(false)
                .maxAge(0)
                .build();

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .build();
    }

}