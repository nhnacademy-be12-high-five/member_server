package com.nhnacademy.member_server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.member_server.controller.CartController;
import com.nhnacademy.member_server.dto.CartAddRequest;
import com.nhnacademy.member_server.dto.CartItemUpdateRequest;
import com.nhnacademy.member_server.dto.CartListResponse;
import com.nhnacademy.member_server.service.CartService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class)
@AutoConfigureMockMvc(addFilters = false) // Security Filter Chain 건너뛰기
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @Autowired
    private ObjectMapper objectMapper;

    // =================================================================
    // 1. 장바구니 담기 (POST /api/cart/items)
    // =================================================================

    @Test
    @DisplayName("[회원] 장바구니 담기 성공 - 쿠키 발급 안됨")
    @WithMockUser(username = "1")
    void addItem_Member() throws Exception {
        // given
        CartAddRequest request = new CartAddRequest(100L, 2);

        // void 메서드이므로 given(...).willReturn(...) 불필요 (Mock은 기본적으로 아무것도 안함)

        // when & then
        mockMvc.perform(post("/api/cart/items")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(cookie().doesNotExist("guestCookie")); // 회원이니 쿠키 굽지 않음

        // verify: 서비스가 올바른 인자(memberId=1, guestId=null)로 호출되었는지 확인
        verify(cartService).addToCart(any(CartAddRequest.class), eq(1L), isNull());
    }

    @Test
    @DisplayName("[비회원] 장바구니 담기 성공 - 최초 진입 시 쿠키 발급")
    @WithAnonymousUser
    void addItem_Guest_New() throws Exception {
        // given
        CartAddRequest request = new CartAddRequest(100L, 1);

        // when & then
        mockMvc.perform(post("/api/cart/items")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(cookie().exists("guestCookie")); // 컨트롤러가 UUID 생성해서 쿠키에 넣었는지 확인

        // verify: 서비스가 호출될 때 guestId 자리에 어떤 문자열(UUID)이라도 들어갔는지 확인
        // memberId는 null이어야 함
        verify(cartService).addToCart(any(CartAddRequest.class), isNull(), anyString());
    }

    @Test
    @DisplayName("[비회원] 장바구니 담기 성공 - 기존 쿠키 보유")
    @WithAnonymousUser
    void addItem_Guest_Existing() throws Exception {
        // given
        String existingCookieId = "guest-existing-id";
        Cookie cookie = new Cookie("guestCookie", existingCookieId);
        CartAddRequest request = new CartAddRequest(100L, 1);

        // when & then
        mockMvc.perform(post("/api/cart/items")
                        .with(csrf())
                        .cookie(cookie) // 요청에 쿠키 포함
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                // 이미 있는 쿠키를 썼으므로, 응답에 Set-Cookie 헤더가 없을 수도 있고(갱신 안함),
                // 로직에 따라 만료시간 갱신을 위해 있을 수도 있음.
                // 현재 컨트롤러 로직상: 쿠키가 있으면 새로 생성 로직을 안 탐 -> Set-Cookie 없음
                .andExpect(cookie().doesNotExist("guestCookie"));

        // verify: 서비스가 기존 쿠키 ID를 그대로 넘겼는지 확인
        verify(cartService).addToCart(any(CartAddRequest.class), isNull(), eq(existingCookieId));
    }

    // =================================================================
    // 2. 장바구니 조회 (GET /api/cart)
    // =================================================================

    @Test
    @DisplayName("[회원] 장바구니 조회 성공")
    @WithMockUser(username = "1")
    void getCartItems() throws Exception {
        // given
        // 조회 메서드는 리턴값이 있으므로 given 필요
        CartListResponse response = new CartListResponse(Collections.emptyList(), 0L);
        given(cartService.getCartItemList(eq(1L), isNull())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/cart")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCartPrice").value(0L));

        verify(cartService).getCartItemList(eq(1L), isNull());
    }

    // =================================================================
    // 3. 수량 변경 (PUT /api/cart/items)
    // =================================================================

    @Test
    @DisplayName("수량 변경 성공")
    @WithMockUser(username = "1")
    void updateQuantity() throws Exception {
        // given
        CartItemUpdateRequest request = new CartItemUpdateRequest(100L, 5);

        // when & then
        mockMvc.perform(put("/api/cart/items")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // void 메서드 검증
        verify(cartService).updateCartItemQuantity(eq(1L), isNull(), any(CartItemUpdateRequest.class));
    }

    @Test
    @DisplayName("수량 변경 실패 - 유효성 검증 (0개 이하)")
    @WithMockUser(username = "1")
    void updateQuantity_Fail_Validation() throws Exception {
        // given
        CartItemUpdateRequest request = new CartItemUpdateRequest(100L, 0);

        // when & then
        mockMvc.perform(put("/api/cart/items")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        // 컨트롤러 진입 전 @Valid에서 걸리므로 서비스 호출 안됨
    }

    // =================================================================
    // 4. 삭제 (DELETE)
    // =================================================================

    @Test
    @DisplayName("단건 삭제 성공")
    @WithMockUser(username = "1")
    void deleteOneItem() throws Exception {
        // given
        Long bookId = 55L;

        // when & then
        mockMvc.perform(delete("/api/cart/items/{bookId}", bookId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(cartService).deleteCartItem(eq(1L), isNull(), eq(bookId));
    }

    @Test
    @DisplayName("전체 삭제 성공")
    @WithMockUser(username = "1")
    void deleteAllCartItem() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/cart/items")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(cartService).deleteAllCartItem(eq(1L), isNull());
    }
}