package com.nhnacademy.member_server.service.impl.member;

import com.nhnacademy.member_server.dto.request.member.MemberUpdateRequest;
import com.nhnacademy.member_server.dto.response.member.MemberResponse;
import com.nhnacademy.member_server.dto.response.member.SimpleMemberResponse;
import com.nhnacademy.member_server.entity.member.Grade;
import com.nhnacademy.member_server.entity.member.Member;
import com.nhnacademy.member_server.entity.member.Role;
import com.nhnacademy.member_server.entity.member.Status;
import com.nhnacademy.member_server.exception.BusinessException;
import com.nhnacademy.member_server.exception.ErrorCode;
import com.nhnacademy.member_server.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest {

    @InjectMocks
    MemberServiceImpl memberService;

    @Mock
    MemberRepository memberRepository;

    @Test
    void withdraw_Success() {
        Long memberId = 1L;
        Member member = Member.builder().id(memberId).status(Status.ACTIVE).build();
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        memberService.withdraw(memberId);

        assertThat(member.getStatus()).isEqualTo(Status.WITHDRAWAL);
    }

    @Test
    void getMember_Success() {
        Long memberId = 1L;
        Member member = Member.builder()
                .id(memberId)
                .name("Test")
                .email("test@test.com")
                .phone("01012345678")
                .birthDate(LocalDate.now())
                .status(Status.ACTIVE)
                .grade(Grade.builder().gradeName("GENERAL").build())
                .build();
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        MemberResponse response = memberService.getMember(memberId);

        assertThat(response.getName()).isEqualTo("Test");
    }

    @Test
    void updateMember_Success() {
        Long memberId = 1L;
        Member member = Member.builder()
                .id(memberId)
                .name("Old")
                .email("old@test.com")
                .phone("01011112222")
                .grade(Grade.builder().gradeName("GENERAL").build())
                .status(Status.ACTIVE)
                .build();

        MemberUpdateRequest request = MemberUpdateRequest.builder()
                .name("New")
                .phone("010-9999-8888")
                .build();

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        MemberResponse response = memberService.updateMember(memberId, request);

        assertThat(response.getName()).isEqualTo("New");
        assertThat(response.getPhone()).isEqualTo("01099998888");
    }

    @Test
    void updateMember_Fail_DuplicateEmail() {
        Long memberId = 1L;
        Member member = Member.builder().id(memberId).email("old@test.com").build();
        MemberUpdateRequest request = MemberUpdateRequest.builder().email("new@test.com").build();

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(memberRepository.existsByEmail("new@test.com")).willReturn(true);

        assertThatThrownBy(() -> memberService.updateMember(memberId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void updateMember_Fail_BirthDateChange() {
        Long memberId = 1L;
        Member member = Member.builder()
                .id(memberId)
                .birthDate(LocalDate.of(2000, 1, 1))
                .isProfileComplete(true)
                .build();
        MemberUpdateRequest request = MemberUpdateRequest.builder()
                .birthDate(LocalDate.of(1990, 1, 1))
                .build();

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.updateMember(memberId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BIRTHDATE_CANNOT_CHANGE);
    }

    @Test
    void getBirthdayMemberIds_Success() {
        List<Long> ids = List.of(1L, 2L);
        given(memberRepository.findAllIdsByBirthMonth(1)).willReturn(ids);

        List<Long> result = memberService.getBirthdayMemberIds(1);

        assertThat(result).hasSize(2);
    }

    @Test
    void getBirthdayMemberIds_Fail_InvalidMonth() {
        assertThatThrownBy(() -> memberService.getBirthdayMemberIds(13))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void updateRole_Success() {
        Long memberId = 1L;
        Member member = Member.builder().id(memberId).role(Role.USER).build();
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        memberService.updateRole(memberId, Role.ADMIN);

        assertThat(member.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void getMembersInfo_Success() {
        List<Long> ids = List.of(1L);
        Object[] row = {1L, "loginId", "name"};

        // [수정] List.of(row) -> Collections.singletonList(row)
        // row가 Object[]라서 List.of 사용 시 List<Object>로 인식되는 문제 해결
        given(memberRepository.findSimpleMembers(ids)).willReturn(Collections.singletonList(row));

        List<SimpleMemberResponse> result = memberService.getMembersInfo(ids);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("name");
    }

    @Test
    void getMembersInfo_Empty() {
        List<SimpleMemberResponse> result = memberService.getMembersInfo(Collections.emptyList());
        assertThat(result).isEmpty();
    }

    @Test
    void checkDormantMember_Success() {
        Member member = Member.builder().status(Status.DORMANT).build();
        given(memberRepository.findByLoginIdAndEmail("test", "t@t.com")).willReturn(Optional.of(member));

        memberService.checkDormantMember("test", "t@t.com");
    }

    @Test
    void checkDormantMember_Fail_NotDormant() {
        Member member = Member.builder().status(Status.ACTIVE).build();
        given(memberRepository.findByLoginIdAndEmail("test", "t@t.com")).willReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.checkDormantMember("test", "t@t.com"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_DORMANT);
    }

    @Test
    void activateDormantMember_Success() {
        Member member = Member.builder().status(Status.DORMANT).build();
        given(memberRepository.findByLoginIdAndEmail("test", "t@t.com")).willReturn(Optional.of(member));

        memberService.activateDormantMember("test", "t@t.com");

        assertThat(member.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(member.getLastLoginAt()).isNotNull();
    }

    @Test
    void activateDormantMember_Fail_Withdrawn() {
        Member member = Member.builder().status(Status.WITHDRAWAL).build();
        given(memberRepository.findByLoginIdAndEmail("test", "t@t.com")).willReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.activateDormantMember("test", "t@t.com"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_WITHDRAWN);
    }

    @Test
    void activateDormantMember_Fail_AlreadyActive() {
        Member member = Member.builder().status(Status.ACTIVE).build();
        given(memberRepository.findByLoginIdAndEmail("test", "t@t.com")).willReturn(Optional.of(member));

        memberService.activateDormantMember("test", "t@t.com");

        assertThat(member.getStatus()).isEqualTo(Status.ACTIVE);
    }
}