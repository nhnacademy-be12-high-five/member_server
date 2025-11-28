package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.dto.response.MemberResponse;

public interface MemberService {
    void withdraw(Long userId);

    MemberResponse getMember(Long memberId);
}
