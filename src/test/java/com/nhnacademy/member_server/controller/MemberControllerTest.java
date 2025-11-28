package com.nhnacademy.member_server.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.member_server.entity.MemberPrincipal;
import com.nhnacademy.member_server.service.impl.AuthServiceImpl;
import com.nhnacademy.member_server.service.impl.MemberServiceImpl;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("local")
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberServiceImpl memberServiceImpl;

    @MockitoBean
    private AuthServiceImpl authServiceImpl;

    @Test
    @DisplayName("마이페이지 조회 (Gateway 헤더 필수)")
    void getMyPage_success() throws Exception {
        Long memberId = 123L;

        mockMvc.perform(get("/members/my-page")
                        .header("X-User-ID", memberId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("회원 탈퇴 성공 - 가장 직관적인 방법")
    void withdraw_success() throws Exception {
        // ------------------------------------------------------------
        // 1. 준비 (Given): 가짜 인증 객체를 직접 만듭니다.
        // ------------------------------------------------------------
        Long memberId = 123L;
        String loginId = "testUser";
        String role = "USER";
        String testToken = "test-access-token";

        // (1) 내가 만든 커스텀 Principal 생성
        MemberPrincipal myPrincipal = new MemberPrincipal(memberId, loginId, role);

        // (2) 스프링 시큐리티가 알아보는 '인증 토큰' 생성
        Authentication auth = new UsernamePasswordAuthenticationToken(
                myPrincipal, // ★ 여기에 우리 객체를 넣는 게 핵심!
                null,        // 비밀번호 (필요 없음)
                Collections.singleton(new SimpleGrantedAuthority("ROLE_" + role)) // 권한
        );

        // ------------------------------------------------------------
        // 2. 실행 (When): .with(authentication(auth))로 주입합니다.
        // ------------------------------------------------------------
        mockMvc.perform(delete("/members/withdraw")
                        .header("X-User-ID", memberId) // (선택) 로직상 필요하면 유지
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + testToken) // 컨트롤러가 토큰 달라고 하니까 줌
                        .with(authentication(auth)) // ★ "이 사람 로그인한 걸로 쳐!" 하고 강제 주입
                        .with(csrf()))              // CSRF 토큰 (POST/DELETE 필수)
                .andDo(print())
                // ------------------------------------------------------------
                // 3. 검증 (Then)
                // ------------------------------------------------------------
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refresh-token", 0));

        // 서비스가 잘 호출됐는지 확인
        verify(memberServiceImpl).withdraw(memberId);
        verify(authServiceImpl).logout(testToken, memberId);
    }
}