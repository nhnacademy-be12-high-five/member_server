package com.nhnacademy.member_server.service.impl;

import com.nhnacademy.member_server.dto.request.MemberUpdateRequest;
import com.nhnacademy.member_server.dto.response.MemberResponse;
import com.nhnacademy.member_server.entity.Gender;
import com.nhnacademy.member_server.entity.Member;
import com.nhnacademy.member_server.entity.Status;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;

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
}
