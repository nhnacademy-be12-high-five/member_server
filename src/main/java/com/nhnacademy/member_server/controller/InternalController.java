package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.entity.Role;
import com.nhnacademy.member_server.service.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;


@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final MemberService memberService;

    @PutMapping("/{member-id}/role")
    public ResponseEntity<String> updateMemberRole(
            @PathVariable("member-id") Long memberId,
            @RequestParam Role role
    ) {
        memberService.updateRole(memberId, role);
        return ResponseEntity.ok("회원(" + memberId + ")의 권한이 " + role + "로 변경되었습니다.");
    }

}
