package com.nhnacademy.member_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.member_server.dto.request.member.MemberUpdateRequest;
import com.nhnacademy.member_server.dto.response.member.MemberResponse;
import com.nhnacademy.member_server.dto.response.member.SimpleMemberResponse;
import com.nhnacademy.member_server.entity.member.Gender;
import com.nhnacademy.member_server.entity.member.Role;
import com.nhnacademy.member_server.service.member.AuthService;
import com.nhnacademy.member_server.service.member.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockUser
@WebMvcTest(controllers = MemberController.class, properties = "jwt.refresh_expiration_time=604800000")
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("내 정보 조회 (GET /api/members/me)")
    void getMemberSuccess() throws Exception {
        Long memberId = 1L;

        MemberResponse response = MemberResponse.builder()
                .name("홍길동")
                .email("test@test.com")
                .phone("010-1234-5678")
                .birthDate(LocalDate.of(1990, 1, 1))
                .status("ACTIVE")
                .gradeName("GENERAL")
                .build();

        given(memberService.getMember(memberId)).willReturn(response);

        mockMvc.perform(get("/api/members/me")
                        .header("X-User-ID", memberId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.email").value("test@test.com"));
    }

    @Test
    @DisplayName("회원 정보 수정 (PATCH /api/members/me)")
    void updateMemberSuccess() throws Exception {
        Long memberId = 1L;

        MemberUpdateRequest request = new MemberUpdateRequest(
                "홍길동",
                "new@email.com",
                "010-9999-9999",
                Gender.FEMALE,
                LocalDate.of(1999, 1, 1)
        );

        MemberResponse updatedResponse = MemberResponse.builder()
                .email("new@email.com")
                .name("홍길동")
                .status("ACTIVE")
                .build();

        given(memberService.updateMember(eq(memberId), any(MemberUpdateRequest.class)))
                .willReturn(updatedResponse);

        mockMvc.perform(patch("/api/members/me")
                        .with(csrf())
                        .header("X-User-ID", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@email.com"));
    }

    @Test
    @DisplayName("회원 탈퇴 (DELETE /api/members/me/withdraw)")
    void withdrawSuccess() throws Exception {
        Long memberId = 1L;
        String accessToken = "Bearer access-token";

        mockMvc.perform(delete("/api/members/me/withdraw")
                        .with(csrf())
                        .header("X-User-ID", memberId)
                        .header(HttpHeaders.AUTHORIZATION, accessToken))
                .andExpect(status().isOk());

        verify(authService).logout(anyString(), eq(memberId));
        verify(memberService).withdraw(memberId);
    }

    @Test
    @DisplayName("생일자 조회 (GET /api/members/birthday)")
    void getBirthdayMemberIdsSuccess() throws Exception {
        int month = 12;
        List<Long> memberIds = List.of(1L, 2L, 3L);
        given(memberService.getBirthdayMemberIds(month)).willReturn(memberIds);

        mockMvc.perform(get("/api/members/birthday")
                        .param("month", String.valueOf(month))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(3))
                .andExpect(jsonPath("$[0]").value(1L));
    }

    @Test
    @DisplayName("회원 권한 변경 (PUT /api/members/{member-id}/role)")
    void updateMemberRoleSuccess() throws Exception {
        Long targetMemberId = 100L;
        Role newRole = Role.ADMIN;

        mockMvc.perform(put("/api/members/{member-id}/role", targetMemberId)
                        .with(csrf())
                        .param("role", newRole.name()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ADMIN")));

        verify(memberService).updateRole(targetMemberId, newRole);
    }

    @Test
    @DisplayName("회원 리스트 단순 조회 (POST /api/members/list)")
    void getMembersInfoSuccess() throws Exception {
        List<Long> memberIds = List.of(1L, 2L);

        List<SimpleMemberResponse> responses = List.of(
                SimpleMemberResponse.builder().memberId(1L).name("user1").build(),
                SimpleMemberResponse.builder().memberId(2L).name("user2").build()
        );

        given(memberService.getMembersInfo(anyList())).willReturn(responses);

        mockMvc.perform(post("/api/members/list")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(memberIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].name").value("user1"));
    }
}