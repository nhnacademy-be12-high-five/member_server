package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.dto.request.MemberUpdateRequest;
import com.nhnacademy.member_server.dto.response.MemberResponse;
import com.nhnacademy.member_server.dto.response.SimpleMemberResponse;
import com.nhnacademy.member_server.entity.Role;

import java.util.List;
import java.util.Map;

public interface MemberService {
    void withdraw(Long userId);

    MemberResponse getMember(Long memberId);

    MemberResponse updateMember(Long memberId, MemberUpdateRequest memberUpdateRequest);

    List<Long> getBirthdayMemberIds(int month);

    void updateRole(Long memberId, Role newRole);

    List<SimpleMemberResponse> getMembersInfo(List<Long> memberIds);
}
