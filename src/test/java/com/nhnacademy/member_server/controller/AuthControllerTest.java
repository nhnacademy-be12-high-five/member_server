package com.nhnacademy.member_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.member_server.dto.request.LoginRequest;
import com.nhnacademy.member_server.dto.request.MemberCreateRequest;
import com.nhnacademy.member_server.dto.response.TokenDto;
import com.nhnacademy.member_server.entity.Gender;
import com.nhnacademy.member_server.entity.Role;
import com.nhnacademy.member_server.service.impl.AuthServiceImpl;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("local")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthServiceImpl authServiceImpl;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("회원가입")
    void signup_success() throws Exception {
        MemberCreateRequest request = new MemberCreateRequest("user1", "1234", "김철수", "010-1234-5678", "test@test.com", Gender.UNKNOWN,LocalDate.now(), Role.USER);

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(authServiceImpl).signup(any(MemberCreateRequest.class));
    }

    @Test
    @DisplayName("로그인 성공 시 토큰 반환")
    void login_success() throws Exception {
        LoginRequest request = new LoginRequest();
        ReflectionTestUtils.setField(request, "loginId", "user1");
        ReflectionTestUtils.setField(request, "password", "1234");

        TokenDto tokenDto = new TokenDto("access-token-value", "refresh-token-value");
        given(authServiceImpl.loginUser(any(), any())).willReturn(tokenDto);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-value"))
                .andExpect(cookie().value("refresh-token", "refresh-token-value"));
    }

    @Test
    @DisplayName("토큰 재발급")
    void reissue_success() throws Exception {
        String refreshToken = "valid-refresh-token";
        TokenDto newToken = new TokenDto("new-access", "new-refresh");

        given(authServiceImpl.reissue(refreshToken)).willReturn(newToken);

        mockMvc.perform(post("/auth/reissue")
                        .cookie(new Cookie("refresh-token", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"));
    }


}