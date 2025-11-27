package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.dto.response.MemberResponse;
import com.nhnacademy.member_server.entity.MemberPrincipal;
import com.nhnacademy.member_server.service.AuthService;
import com.nhnacademy.member_server.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final AuthService authService;

    @GetMapping
    public ResponseEntity<MemberResponse> getMember(@AuthenticationPrincipal MemberPrincipal principal) {
        Long memberId = principal.getMemberId();
        MemberResponse response = memberService.getMember(memberId);
        return ResponseEntity.ok(response);
    }

    //테스트용
    @GetMapping("/my-page")
    public ResponseEntity<String> getMyPage(
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        Long memberId = principal.getMemberId();
        String role = principal.getRole();
        String loginId = principal.getLoginId();
        return ResponseEntity.ok("마이페이지 접근 성공! 당신의 member_Id는: " + memberId + " role : " + role + " Login_id : "  + loginId);
    }

    //어드민 테스트용
    @GetMapping("/admin/my-page")
    public ResponseEntity<String> getAdminMyPage(
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        Long memberId = principal.getMemberId();
        String role = principal.getRole();
        String loginId = principal.getLoginId();
        return ResponseEntity.ok("admin 마이페이지 접근 성공! 당신의 member_Id는: " + memberId + " role : " + role + " Login_id : "  + loginId);
    }


    @DeleteMapping("/withdraw")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal MemberPrincipal principal) {
        Long memberId = principal.getMemberId();

        memberService.withdraw(memberId);
        authService.logout(memberId);

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