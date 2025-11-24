package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.entity.Member;
import com.nhnacademy.member_server.entity.Status;
import com.nhnacademy.member_server.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional
    public void withdraw(Long userId) {
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("회원을 찾을 수 없습니다"));

        member.setStatus(Status.WITHDRAWAL);

    }
}
