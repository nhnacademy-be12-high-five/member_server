package com.nhnacademy.member_server;

import com.nhnacademy.member_server.controller.CartController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.member_server.dto.CartAddRequest;
import com.nhnacademy.member_server.dto.CartItemUpdateRequest;
import com.nhnacademy.member_server.dto.CartListResponse;
import com.nhnacademy.member_server.service.CartService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class) // Controller만 테스트
@AutoConfigureMockMvc(addFilters = false) // Security Filter Chain 건너뛰기 (단순화)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartService cartService;

    @Autowired
    private ObjectMapper objectMapper; // 객체 <-> JSON 변환용

    // =================================================================
    // 1. 장바구니 담기 (POST /api/cart/items)
    // =================================================================

    @Test
    @DisplayName("[회원] 장바구니 담기 성공 - 쿠키 발급 안됨")
    @WithMockUser(username = "1") // getMemberId()가 1L을 반환하게 됨
    void addItem_Member() throws Exception {
        // given
        CartAddRequest request = new CartAddRequest(100L, 2); // bookId: 100, quantity: 2

        // 회원은 이미 ID가 있으므로 서비스는 null(새 쿠키 ID)을 반환한다고 가정
        given(cartService.addBookToCart(any(), eq(1L), isNull()))
                .willReturn(null);

        // when & then
        mockMvc.perform(post("/api/cart/items")
                        .with(csrf()) // CSRF 토큰 (POST 필수)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(cookie().doesNotExist("guestCookie")); // 쿠키가 없어야 함
    }

    @Test
    @DisplayName("[비회원] 장바구니 담기 성공 - 최초 진입 시 쿠키 발급")
    @WithAnonymousUser // 비회원 상태
    void addItem_Guest_New() throws Exception {
        // given
        CartAddRequest request = new CartAddRequest(100L, 1);
        String newGuestId = "guest-uuid-1234";

        // 비회원(memberId=null) & 쿠키없음(guestId=null) -> 새 ID 반환 가정
        given(cartService.addBookToCart(any(), isNull(), isNull()))
                .willReturn(newGuestId);

        // when & then
        mockMvc.perform(post("/api/cart/items")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(cookie().exists("guestCookie")) // 쿠키가 구워져야 함
                .andExpect(cookie().value("guestCookie", newGuestId));
    }

    @Test
    @DisplayName("[비회원] 장바구니 담기 성공 - 기존 쿠키 보유")
    @WithAnonymousUser
    void addItem_Guest_Existing() throws Exception {
        // given
        String existingCookieId = "guest-existing-id";
        Cookie cookie = new Cookie("guestCookie", existingCookieId);
        CartAddRequest request = new CartAddRequest(100L, 1);

        // 기존 쿠키가 있으니 서비스는 null을 반환 (새로 만들 필요 없음)
        given(cartService.addBookToCart(any(), isNull(), eq(existingCookieId)))
                .willReturn(null);

        // when & then
        mockMvc.perform(post("/api/cart/items")
                        .with(csrf())
                        .cookie(cookie) // 요청에 쿠키 포함
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                // 응답에는 Set-Cookie가 없어야 함 (이미 있으니까)
                // (주의: 로직에 따라 갱신을 위해 또 구울 수도 있음. 본인 로직 확인 필요)
                .andExpect(cookie().doesNotExist("guestCookie"));
    }

    // =================================================================
    // 2. 장바구니 조회 (GET /api/cart)
    // =================================================================

    @Test
    @DisplayName("[회원] 장바구니 조회 성공")
    @WithMockUser(username = "1")
    void getCartItems() throws Exception {
        // given
        CartListResponse response = new CartListResponse(Collections.emptyList(), 0L);
        given(cartService.getCartItemList(eq(1L), isNull())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/cart")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCartPrice").value(0L)); // JSON 필드 검증
    }

    // =================================================================
    // 3. 수량 변경 (PUT /api/cart/items)
    // =================================================================

    @Test
    @DisplayName("수량 변경 성공")
    @WithMockUser(username = "1")
    void updateQuantity() throws Exception {
        // given
        CartItemUpdateRequest request = new CartItemUpdateRequest(100L, 5); // 5개로 변경

        // when & then
        mockMvc.perform(put("/api/cart/items")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // 서비스가 올바른 파라미터로 호출되었는지 검증
        verify(cartService).updateCartItemQuantity(eq(1L), isNull(), any(CartItemUpdateRequest.class));
    }

    @Test
    @DisplayName("수량 변경 실패 - 유효성 검증 (0개 이하)")
    @WithMockUser(username = "1")
    void updateQuantity_Fail_Validation() throws Exception {
        // given
        // @Min(1) 조건에 위배되는 0개 요청
        CartItemUpdateRequest request = new CartItemUpdateRequest(100L, 0);

        // when & then
        mockMvc.perform(put("/api/cart/items")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()); // 400 Error 기대
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
