package com.nhnacademy.member_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.member_server.dto.request.member.MemberUpdateRequest;
import com.nhnacademy.member_server.dto.response.member.MemberResponse;
import com.nhnacademy.member_server.entity.Gender;
import com.nhnacademy.member_server.entity.MemberPrincipal;
import com.nhnacademy.member_server.service.member.AuthService;
import com.nhnacademy.member_server.service.member.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MemberService memberService;

    @Mock
    private AuthService authService;

    @InjectMocks
    private MemberController memberController;

    private ObjectMapper objectMapper = new ObjectMapper();

    private HandlerMethodArgumentResolver putPrincipal() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().isAssignableFrom(MemberPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return new MemberPrincipal(1L, "testUser", "USER");
            }
        };
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(memberController)
                .setCustomArgumentResolvers(putPrincipal())
                .build();
    }

    @Test
    @DisplayName("내 정보 조회 성공 (GET /api/members)")
    void getMember_success() throws Exception {
        Long memberId = 1L;
        MemberResponse response = MemberResponse.builder()
                .name("홍길동")
                .email("test@nhn.com")
                .phone("010-1234-5678")
                .birthDate(LocalDate.of(1999, 1, 1))
                .status("ACTIVE")
                .gradeName("GOLD")
                .build();

        given(memberService.getMember(memberId)).willReturn(response);

        mockMvc.perform(get("/api/members"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.gradeName").value("GOLD"));

        verify(memberService).getMember(memberId);
    }

    @Test
    @DisplayName("내 정보 수정 성공 (PATCH /api/members)")
    void updateMember_success() throws Exception {
        Long memberId = 1L;
        MemberUpdateRequest request = MemberUpdateRequest.builder()
                .email("update@nhn.com")
                .phone("010-9999-9999")
                .gender(Gender.MALE)
                .build();

        MemberResponse updatedResponse = MemberResponse.builder()
                .name("홍길동")
                .email("update@nhn.com")
                .phone("010-9999-9999")
                .build();

        given(memberService.updateMember(eq(memberId), any(MemberUpdateRequest.class)))
                .willReturn(updatedResponse);

        mockMvc.perform(patch("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("update@nhn.com"));

        verify(memberService).updateMember(eq(memberId), any(MemberUpdateRequest.class));
    }

    @Test
    @DisplayName("회원 탈퇴 성공 (DELETE /api/members/withdraw)")
    void withdraw_success() throws Exception {
        Long memberId = 1L;
        String testToken = "test-access-token";

        mockMvc.perform(delete("/api/members/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + testToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refresh-token", 0));

        verify(memberService).withdraw(memberId);
        verify(authService).logout(testToken, memberId);
    }


}