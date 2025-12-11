//package com.nhnacademy.member_server.controller;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.nhnacademy.member_server.dto.cartRequest.CartAddRequest;
//import com.nhnacademy.member_server.dto.cartRequest.CartItemUpdateRequest;
//import com.nhnacademy.member_server.dto.cartResponse.CartAddResponse;
//import com.nhnacademy.member_server.dto.cartResponse.CartListResponse;
//import com.nhnacademy.member_server.dto.cartResponse.CartUpdateResponse;
//import com.nhnacademy.member_server.service.CartService;
//import jakarta.servlet.http.Cookie;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.http.MediaType;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
//import org.springframework.test.web.servlet.MockMvc;
//import org.springframework.test.web.servlet.ResultActions;
//
//import java.util.Collections;
//
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.BDDMockito.given;
//import static org.mockito.Mockito.verify;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
//import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
//@WebMvcTest(CartController.class)
//@AutoConfigureMockMvc(addFilters = false) // Security Filter 무시 (순수 컨트롤러 로직 테스트)
//class CartControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @MockitoBean
//    private CartService cartService;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    // =================================================================
//    // [테스트 1] 장바구니 담기 (POST /api/cart/items)
//    // =================================================================
//
//    @Test
//    @DisplayName("장바구니 담기 - 비회원(쿠키없음) -> 새 쿠키 생성 및 저장 성공")
//    void addItemToCart_Guest_NoCookie() throws Exception {
//        // given
//        CartAddRequest request = new CartAddRequest(100L, 2);
//        CartAddResponse response = new CartAddResponse("cart:g:new-uuid", 100L, 2);
//
//        // Service Mocking (guestId는 UUID로 생성되므로 anyString() 처리)
//        given(cartService.addToCart(any(CartAddRequest.class), isNull(), anyString()))
//                .willReturn(response);
//
//        // when
//        ResultActions result = mockMvc.perform(post("/api/cart/items")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(objectMapper.writeValueAsString(request)));
//
//        // then
//        result.andExpect(status().isCreated())
//                .andExpect(cookie().exists("guestCookie")) // 쿠키가 생성되었는지 확인
//                .andExpect(cookie().maxAge("guestCookie", 60 * 60 * 24 * 7)) // 유효기간 확인
//                .andExpect(jsonPath("$.bookId").value(100L))
//                .andDo(print());
//    }
//
//    @Test
//    @DisplayName("장바구니 담기 - 비회원(쿠키있음) -> 기존 쿠키 유지 및 저장 성공")
//    void addItemToCart_Guest_WithCookie() throws Exception {
//        // given
//        String guestCookie = "existing-guest-id";
//        CartAddRequest request = new CartAddRequest(100L, 1);
//        CartAddResponse response = new CartAddResponse("cart:g:" + guestCookie, 100L, 1);
//
//        given(cartService.addToCart(any(CartAddRequest.class), isNull(), eq(guestCookie)))
//                .willReturn(response);
//
//        // when
//        ResultActions result = mockMvc.perform(post("/api/cart/items")
//                .cookie(new Cookie("guestCookie", guestCookie)) // 쿠키 포함 요청
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(objectMapper.writeValueAsString(request)));
//
//        // then
//        result.andExpect(status().isCreated())
//                .andExpect(jsonPath("$.key").value("cart:g:" + guestCookie)) // key 필드를 찾음 (성공 ✅)
//                .andDo(print());
//    }
//
//    // =================================================================
//    // [테스트 2] 장바구니 조회 (GET /api/cart)
//    // =================================================================
//
//    @Test
//    @DisplayName("장바구니 조회 - 성공적으로 리스트 반환")
//    void getCartItems() throws Exception {
//        // given
//        String guestId = "guest-123";
//        CartListResponse response = new CartListResponse(Collections.emptyList(), 0L, false);
//
//        given(cartService.getCartItemList(isNull(), eq(guestId))).willReturn(response);
//
//        // when
//        ResultActions result = mockMvc.perform(get("/api/cart")
//                .cookie(new Cookie("guestCookie", guestId)));
//
//        // then
//        result.andExpect(status().isOk())
//                .andExpect(jsonPath("$.totalCartPrice").value(0));
//    }
//
//    // =================================================================
//    // [테스트 3] 수량 변경 (PUT /api/cart/items)
//    // =================================================================
//
//    @Test
//    @DisplayName("수량 변경 - 정상 처리")
//    void updateQuantity() throws Exception {
//        // given
//        String guestId = "guest-123";
//        CartItemUpdateRequest request = new CartItemUpdateRequest(100L, 5);
//        CartUpdateResponse response = new CartUpdateResponse("key", 100L, 5);
//
//        given(cartService.updateCartItemQuantity(isNull(), eq(guestId), any(CartItemUpdateRequest.class)))
//                .willReturn(response);
//
//        // when
//        ResultActions result = mockMvc.perform(put("/api/cart/items")
//                .cookie(new Cookie("guestCookie", guestId))
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(objectMapper.writeValueAsString(request)));
//
//        // then
//        result.andExpect(status().isOk())
//                .andExpect(jsonPath("$.quantity").value(5));
//    }
//
//    // =================================================================
//    // [테스트 4] 장바구니 병합 및 쿠키 삭제 (POST /api/cart/merge)
//    // =================================================================
//
//    @Test
//    @DisplayName("장바구니 병합 - 성공 시 게스트 쿠키 삭제")
//    void mergeGuestCart() throws Exception {
//        // given
//        // *주의* MemberPrincipal 주입을 위해선 별도의 Mock 설정이 필요하지만,
//        // 여기서는 @AuthenticationPrincipal 무시되고 memberId=null, guestId!=null 로직 흐름 테스트
//        // 실제로는 SecurityContextHolder에 Principal을 넣어줘야 함.
//
//        String guestId = "guest-to-delete";
//
//        // when
//        ResultActions result = mockMvc.perform(post("/api/cart/merge")
//                .cookie(new Cookie("guestCookie", guestId)));
//
//        // then
//        verify(cartService).migrateGuestCart(eq(guestId), isNull()); // 호출 검증
//
//        result.andExpect(status().isOk())
//                .andExpect(cookie().maxAge("guestCookie", 0)); // ★ 쿠키 삭제 확인 (Max-Age=0)
//    }
//
//    // =================================================================
//    // [테스트 5] 비회원 장바구니 무시/삭제 (DELETE /api/cart/guest)
//    // =================================================================
//
//    @Test
//    @DisplayName("비회원 장바구니 삭제 - 성공 시 쿠키 삭제")
//    void ignoreGuestCart() throws Exception {
//        // given
//        String guestId = "guest-trash";
//
//        // when
//        ResultActions result = mockMvc.perform(delete("/api/cart/guest")
//                .cookie(new Cookie("guestCookie", guestId)));
//
//        // then
//        verify(cartService).deleteGuestCartOnly(eq(guestId));
//
//        result.andExpect(status().isNoContent())
//                .andExpect(cookie().maxAge("guestCookie", 0)); // ★ 쿠키 삭제 확인
//    }
//}