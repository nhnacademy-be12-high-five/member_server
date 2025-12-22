//package com.nhnacademy.member_server.controller;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.nhnacademy.member_server.dto.request.member.LoginRequest;
//import com.nhnacademy.member_server.dto.request.member.MemberCreateRequest;
//import com.nhnacademy.member_server.dto.response.member.TokenDto;
//import com.nhnacademy.member_server.entity.member.Gender;
//import com.nhnacademy.member_server.entity.member.Role;
//import com.nhnacademy.member_server.repository.MemberRepository;
//import com.nhnacademy.member_server.service.member.AuthService;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.http.MediaType;
//import org.springframework.test.web.servlet.MockMvc;
//import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
//import java.time.LocalDate;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.BDDMockito.given;
//import static org.mockito.Mockito.verify;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@WebMvcTest(controllers = AuthController.class, properties = "jwt.refresh_expiration_time=604800000")
//@AutoConfigureMockMvc(addFilters = false)
//class AuthControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @MockBean
//    private AuthService authService;
//
//    @MockBean
//    private MemberRepository memberRepository;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @Test
//    @DisplayName("로그인 성공 테스트")
//    void loginSuccess() throws Exception {
//        LoginRequest request = new LoginRequest("testId", "password");
//        TokenDto tokenDto = new TokenDto("access-token", "refresh-token", true);
//        given(authService.loginUser(any(), any())).willReturn(tokenDto);
//
//        mockMvc.perform(post("/api/auth/login")
//                        .with(csrf())
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.accessToken").value("access-token"));
//    }
//
//    @Test
//    @DisplayName("회원가입 성공 테스트")
//    void signupSuccess() throws Exception {
//        MemberCreateRequest request = new MemberCreateRequest(
//                "testId",
//                "pw",
//                "name",
//                "01012345678",
//                "email@test.com",
//                Gender.MALE,
//                LocalDate.now(),
//                Role.USER
//        );
//
//        mockMvc.perform(post("/api/auth/signup")
//                        .with(csrf())
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isCreated());
//
//        verify(authService).signup(any(MemberCreateRequest.class));
//    }
//}