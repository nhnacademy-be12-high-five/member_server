package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.service.impl.AuthServiceImpl;
import com.nhnacademy.member_server.service.impl.MemberServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
        Long userId = 123L;

        mockMvc.perform(get("/users/my-page")
                        .header("X-User-ID", userId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("회원 탈퇴 (DB상태변경 + Redis삭제 + 쿠키삭제)")
    void withdraw_success() throws Exception {
        Long userId = 123L;

        mockMvc.perform(delete("/users/withdraw")
                        .header("X-User-ID", userId))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refresh-token", 0));

        verify(memberServiceImpl).withdraw(userId);
        verify(authServiceImpl).logout(userId);
    }
}