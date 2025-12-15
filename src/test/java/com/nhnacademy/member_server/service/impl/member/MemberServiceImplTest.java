package com.nhnacademy.member_server.service.impl.member;

import com.nhnacademy.member_server.dto.request.member.MemberUpdateRequest;
import com.nhnacademy.member_server.dto.response.member.MemberResponse;
import com.nhnacademy.member_server.entity.member.Grade;
import com.nhnacademy.member_server.entity.member.Member;
import com.nhnacademy.member_server.entity.member.Role;
import com.nhnacademy.member_server.entity.member.Status;
import com.nhnacademy.member_server.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest {

    @InjectMocks
    private MemberServiceImpl memberService;

    @Mock
    private MemberRepository memberRepository;

    @Test
    @DisplayName("회원 조회 성공")
    void getMemberSuccess() {
        Long memberId = 1L;
        Member member = Member.builder()
                .id(memberId)
                .name("홍길동")
                .email("test@test.com")
                .status(Status.ACTIVE)
                .grade(Grade.builder().gradeName("GENERAL").build())
                .role(Role.USER)
                .build();

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        MemberResponse response = memberService.getMember(memberId);

        assertThat(response.getName()).isEqualTo("홍길동");
        assertThat(response.getEmail()).isEqualTo("test@test.com");
        assertThat(response.getGradeName()).isEqualTo("GENERAL");
    }

    @Test
    @DisplayName("회원 정보 수정 실패 - 이메일 중복")
    void updateMemberFail_DuplicateEmail() {
        Long memberId = 1L;
        Member member = Member.builder().id(memberId).email("old@test.com").phone("010").build();

        MemberUpdateRequest request = MemberUpdateRequest.builder()
                .email("duplicate@test.com").build();

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(memberRepository.existsByEmail("duplicate@test.com")).willReturn(true);

        assertThatThrownBy(() -> memberService.updateMember(memberId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 사용 중인 이메일");
    }

    @Test
    @DisplayName("회원 탈퇴 처리")
    void withdrawSuccess() {
        Long memberId = 1L;
        Member member = Member.builder().id(memberId).status(Status.ACTIVE).build();
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        memberService.withdraw(memberId);

        assertThat(member.getStatus()).isEqualTo(Status.WITHDRAWAL);
    }

    @Test
    @DisplayName("생일자 ID 목록 조회")
    void getBirthdayMemberIdsSuccess() {
        int month = 12;
        given(memberRepository.findAllIdsByBirthMonth(month)).willReturn(List.of(1L, 2L));

        List<Long> ids = memberService.getBirthdayMemberIds(month);

        assertThat(ids).hasSize(2);
        assertThat(ids).contains(1L, 2L);
    }
}