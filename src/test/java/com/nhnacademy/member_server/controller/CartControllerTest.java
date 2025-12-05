package com.nhnacademy.member_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.member_server.dto.cartRequest.CartAddRequest;
import com.nhnacademy.member_server.dto.cartRequest.CartItemUpdateRequest;
import com.nhnacademy.member_server.dto.cartResponse.CartAddResponse;
import com.nhnacademy.member_server.dto.cartResponse.CartListResponse;
import com.nhnacademy.member_server.dto.cartResponse.CartUpdateResponse;
import com.nhnacademy.member_server.entity.MemberPrincipal;
import com.nhnacademy.member_server.service.CartService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean; // 여기가 핵심 변경 포인트
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContext;

@WebMvcTest(CartController.class)
@Import(CartControllerTest.TestSecurityConfig.class)
class CartControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable) // CSRF 귀찮으면 끄기 (선택)
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().permitAll() // 모든 요청 통과! (403 에러 방지)
                    );
            return http.build();
        }
    }

    // 테스트용 상수
    private static final Long MEMBER_ID = 1L;
    private static final String GUEST_ID_COOKIE = "guest-uuid-1234";

    /**
     * Helper: 가짜 MemberPrincipal 생성
     */
    private MemberPrincipal createMemberPrincipal() {
        // 생성자는 본인 코드에 맞게 수정하세요 (UserDetails 구현체여야 함)
        return new MemberPrincipal(MEMBER_ID, "test@test.com", "ROLE_USER");
    }

    private UsernamePasswordAuthenticationToken createAuthToken(MemberPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities() // MemberPrincipal 안의 getAuthorities() 사용
        );
    }

    @Test
    @DisplayName("[POST] 장바구니 담기 - 회원 (쿠키 생성 X)")
    void addItemToCart_Member() throws Exception {
        // given
        CartAddRequest request = new CartAddRequest(100L, 2);
        CartAddResponse response = new CartAddResponse("guest", 100L, 2);
        MemberPrincipal memberPrincipal = createMemberPrincipal();
        given(cartService.addToCart(any(CartAddRequest.class), eq(MEMBER_ID), any()))
                .willReturn(response);

        // when & then
        mockMvc.perform(post("/cart/items")
                        .with(csrf())
                        .with(authentication(createAuthToken(memberPrincipal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(cookie().doesNotExist("guestCookie"));
    }

    @Test
    @DisplayName("[POST] 장바구니 담기 - 비회원 (쿠키 자동 생성)")
    void addItemToCart_Guest_NewCookie() throws Exception {
        // given
        CartAddRequest request = new CartAddRequest(100L, 1);
        CartAddResponse response = new CartAddResponse("guest", 100L, 1);

        given(cartService.addToCart(any(CartAddRequest.class), isNull(), anyString()))
                .willReturn(response);

        // when & then
        mockMvc.perform(post("/cart/items")
                        .with(csrf())
                        // 인증 정보 없음 -> 비회원
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(cookie().exists("guestCookie"))
                .andExpect(cookie().maxAge("guestCookie", 60 * 60 * 24 * 7));
    }

    @Test
    @DisplayName("[GET] 장바구니 조회 - 비회원 (기존 쿠키 보유)")
    void getCartItems_Guest_WithCookie() throws Exception {
        // given
        CartListResponse mockListResponse = new CartListResponse(Collections.emptyList(), 100000, false);

        given(cartService.getCartItemList(isNull(), eq(GUEST_ID_COOKIE)))
                .willReturn(mockListResponse);

        // when & then
        mockMvc.perform(get("/cart")
                        .cookie(new Cookie("guestCookie", GUEST_ID_COOKIE)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(cartService).getCartItemList(isNull(), eq(GUEST_ID_COOKIE));
    }

    @Test
    @DisplayName("[PUT] 수량 변경")
    void updateQuantity() throws Exception {
        // given
        CartItemUpdateRequest request = new CartItemUpdateRequest(100L, 5);
        CartUpdateResponse response = new CartUpdateResponse("guest",100L, 5);
        MemberPrincipal memberPrincipal = createMemberPrincipal();
        given(cartService.updateCartItemQuantity(eq(MEMBER_ID), isNull(), any(CartItemUpdateRequest.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(put("/cart/items")
                        .with(csrf())
                        .with(authentication(createAuthToken(memberPrincipal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("[DELETE] 단건 삭제")
    void deleteOneItem() throws Exception {
        // given
        Long bookId = 123L;
        MemberPrincipal memberPrincipal = createMemberPrincipal();
        // when & then
        mockMvc.perform(delete("/cart/items/{bookId}", bookId)
                        .with(csrf())
                        .with(authentication(createAuthToken(memberPrincipal))))
                .andExpect(status().isNoContent());

        verify(cartService).deleteCartItem(eq(MEMBER_ID), isNull(), eq(bookId));
    }

    @Test
    @DisplayName("[DELETE] 전체 삭제")
    void deleteAllCartItem() throws Exception {
        MemberPrincipal memberPrincipal = createMemberPrincipal();
        mockMvc.perform(delete("/cart/items")
                        .with(csrf())
                        .with(authentication(createAuthToken(memberPrincipal))))
                .andExpect(status().isNoContent());

        verify(cartService).deleteAllCartItem(eq(MEMBER_ID), isNull());
    }

    @Test
    @DisplayName("[POST] 비회원 장바구니 합치기 (Merge)")
    void mergeGuestCart() throws Exception {
        MemberPrincipal memberPrincipal = createMemberPrincipal();
        // when & then
        mockMvc.perform(post("/cart/merge")
                        .with(csrf())
                        .with(authentication(createAuthToken(memberPrincipal)))
                        .cookie(new Cookie("guestCookie", GUEST_ID_COOKIE)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("guestCookie", 0)); // 쿠키 삭제 명령

        verify(cartService).migrateGuestCart(eq(GUEST_ID_COOKIE), eq(MEMBER_ID));
    }

    @Test
    @DisplayName("[DELETE] 비회원 장바구니 무시 (Ignore)")
    void ignoreGuestCart() throws Exception {
        // when & then
        mockMvc.perform(delete("/cart/guest")
                        .with(csrf())
                        .cookie(new Cookie("guestCookie", GUEST_ID_COOKIE)))
                .andDo(print())
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("guestCookie", 0));

        verify(cartService).deleteGuestCartOnly(eq(GUEST_ID_COOKIE));
    }
}
