package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.dto.request.MemberUpdateRequest;
import com.nhnacademy.member_server.dto.response.MemberResponse;
import com.nhnacademy.member_server.dto.response.SimpleMemberResponse;
import com.nhnacademy.member_server.entity.MemberPrincipal;
import com.nhnacademy.member_server.entity.Role;
import com.nhnacademy.member_server.global.jwt.WebUtils;
import com.nhnacademy.member_server.service.AuthService;
import com.nhnacademy.member_server.service.MemberService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<MemberResponse> getMember(@AuthenticationPrincipal MemberPrincipal principal) {
        Long memberId = principal.getMemberId();
        MemberResponse response = memberService.getMember(memberId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    public ResponseEntity<MemberResponse> updateMember(@AuthenticationPrincipal MemberPrincipal principal,
                                                       @Valid @RequestBody MemberUpdateRequest memberUpdateRequest) {
        Long memberId = principal.getMemberId();
        MemberResponse memberResponse = memberService.updateMember(memberId, memberUpdateRequest);
        return ResponseEntity.ok(memberResponse);
    }


    @DeleteMapping("me/withdraw")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal MemberPrincipal principal,
                                         @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerHeader) {
        Long memberId = principal.getMemberId();
        authService.logout(WebUtils.getToken(bearerHeader), memberId);
        memberService.withdraw(memberId);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/birthday")
    public ResponseEntity<List<Long>> getBirthdayMemberIds(
            @RequestParam("month") int month
    ) {
        List<Long> memberIds = memberService.getBirthdayMemberIds(month);

        return ResponseEntity.ok(memberIds);
    }

    @PutMapping("/{member-id}/role")
    public ResponseEntity<String> updateMemberRole(
            @PathVariable("member-id") Long memberId,
            @RequestParam Role role
    ) {
        memberService.updateRole(memberId, role);
        return ResponseEntity.ok("회원(" + memberId + ")의 권한이 " + role + "로 변경되었습니다.");
    }

    @PostMapping("/list")
    public ResponseEntity<List<SimpleMemberResponse>> getMembersInfo(@RequestBody List<Long> memberIds) {
        List<SimpleMemberResponse> responseList = memberService.getMembersInfo(memberIds);
        return ResponseEntity.ok(responseList);
    }
}