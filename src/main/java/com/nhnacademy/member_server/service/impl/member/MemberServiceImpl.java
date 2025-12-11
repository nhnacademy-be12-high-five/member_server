package com.nhnacademy.member_server.service.impl.member;

import com.nhnacademy.member_server.dto.request.member.MemberUpdateRequest;
import com.nhnacademy.member_server.dto.response.member.MemberResponse;
import com.nhnacademy.member_server.dto.response.member.SimpleMemberResponse;
import com.nhnacademy.member_server.entity.member.Member;
import com.nhnacademy.member_server.entity.member.Role;
import com.nhnacademy.member_server.entity.member.Status;
import com.nhnacademy.member_server.exception.BusinessException;
import com.nhnacademy.member_server.exception.ErrorCode;
import com.nhnacademy.member_server.repository.AddressRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.service.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final AddressRepository addressRepository;

    @Override
    @Transactional
    public void withdraw(Long userId) {
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("회원을 찾을 수 없습니다"));

        member.setStatus(Status.WITHDRAWAL);

    }

    @Override
    @Transactional(readOnly = true)
    public MemberResponse getMember(Long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new RuntimeException("존재하지 않는 member 조회"));
        MemberResponse memberResponse = MemberResponse.from(member);
        return memberResponse;
    }

    @Override
    @Transactional
    public MemberResponse updateMember(Long memberId, MemberUpdateRequest memberUpdateRequest) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new RuntimeException("존재하지 않는 member 조회"));

        if(memberUpdateRequest.getEmail() != null &&
                !memberUpdateRequest.getEmail().equals(member.getEmail()) &&
                memberRepository.existsByEmail(memberUpdateRequest.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }


        if(memberUpdateRequest.getPhone() != null &&
                !memberUpdateRequest.getPhone().equals(member.getPhone()) &&
                memberRepository.existsByPhone(memberUpdateRequest.getPhone())) {
            throw new IllegalArgumentException("이미 사용 중인 전화번호입니다.");
        }

        if(memberUpdateRequest.getEmail() != null) member.setEmail(memberUpdateRequest.getEmail());
        if(memberUpdateRequest.getPhone() != null) member.setPhone(memberUpdateRequest.getPhone());
        if(memberUpdateRequest.getGender() != null) member.setGender(memberUpdateRequest.getGender());


        return MemberResponse.from(member);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getBirthdayMemberIds(int month) {
        if (month < 1 || month > 12) {
            throw new RuntimeException("1월에서 12월 사이여야 합니다.");
        }
        return memberRepository.findAllIdsByBirthMonth(month);
    }

    @Override
    @Transactional
    public void updateRole(Long memberId, Role newRole) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        member.setRole(newRole);
    }


    @Override
    @Transactional(readOnly = true)
    public List<SimpleMemberResponse> getMembersInfo(List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Object[]> results = memberRepository.findSimpleMembers(memberIds);

        return results.stream()
                .map(row -> SimpleMemberResponse.builder()
                        .memberId((Long) row[0])
                        .loginId((String) row[1])
                        .build())
                .toList();
    }

}
