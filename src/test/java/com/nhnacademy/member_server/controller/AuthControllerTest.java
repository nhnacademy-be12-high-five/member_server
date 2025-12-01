package com.nhnacademy.member_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.member_server.dto.request.LoginRequest;
import com.nhnacademy.member_server.dto.request.MemberCreateRequest;
import com.nhnacademy.member_server.dto.response.TokenDto;
import com.nhnacademy.member_server.entity.Gender;
import com.nhnacademy.member_server.entity.Role;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class, properties = "jwt.refresh_expiration_time=10000")
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private MemberRepository memberRepository;

    @Test
    @DisplayName("회원가입 성공")
    void signup_success() throws Exception {
        MemberCreateRequest request = new MemberCreateRequest(
                "user1", "1234", "김철수", "010-1234-5678",
                "test@test.com", Gender.UNKNOWN, LocalDate.now(), Role.USER
        );

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isCreated());

        verify(authService).signup(any(MemberCreateRequest.class));
    }

    @Test
    @DisplayName("로그인 성공 시 토큰 반환")
    void login_success() throws Exception {
        LoginRequest request = new LoginRequest();
        ReflectionTestUtils.setField(request, "loginId", "user1");
        ReflectionTestUtils.setField(request, "password", "1234");

        TokenDto tokenDto = new TokenDto("access-token-value", "refresh-token-value");

        given(authService.loginUser(any(), any())).willReturn(tokenDto);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-value"))
                .andExpect(cookie().value("refresh-token", "refresh-token-value"));
    }

    @Test
    @DisplayName("토큰 재발급 성공")
    void reissue_success() throws Exception {
        String refreshToken = "valid-refresh-token";
        TokenDto newToken = new TokenDto("new-access", "new-refresh");

        given(authService.reissue(refreshToken)).willReturn(newToken);

        mockMvc.perform(post("/api/auth/reissue")
                        .cookie(new Cookie("refresh-token", refreshToken))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"));
    }

    @Test
    @DisplayName("로그아웃 성공")
    void logout_success() throws Exception {
        long memberId = 123L;
        String testToken = "test-access-token";

        mockMvc.perform(post("/api/auth/logout")
                        .header("X-User-ID", memberId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + testToken)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refresh-token", 0));

        verify(authService).logout(testToken, memberId);
    }

    @Test
    @DisplayName("아이디 중복 체크")
    void checkId_success() throws Exception {
        String loginId = "duplicateUser";
        given(memberRepository.existsByLoginId(loginId)).willReturn(true);

        mockMvc.perform(get("/api/auth/check-id/{loginId}", loginId)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(content().string("true"));
    }
}