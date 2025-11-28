package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.dto.request.MemberUpdateRequest;
import com.nhnacademy.member_server.dto.response.MemberResponse;

public interface MemberService {
    void withdraw(Long userId);

    MemberResponse getMember(Long memberId);

    MemberResponse updateMember(Long memberId, MemberUpdateRequest memberUpdateRequest);
}
