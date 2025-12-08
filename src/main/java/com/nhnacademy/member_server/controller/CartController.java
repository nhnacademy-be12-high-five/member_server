package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.dto.cartRequest.CartAddRequest;
import com.nhnacademy.member_server.dto.cartRequest.CartItemUpdateRequest;
import com.nhnacademy.member_server.dto.cartResponse.CartAddResponse;
import com.nhnacademy.member_server.dto.cartResponse.CartListResponse;
import com.nhnacademy.member_server.dto.cartResponse.CartUpdateResponse;
import com.nhnacademy.member_server.entity.MemberPrincipal;
import com.nhnacademy.member_server.entity.cartEntity.Cart;
import com.nhnacademy.member_server.service.CartService;
import com.nhnacademy.member_server.utils.CookieUtils;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController{

    private final CartService cartService;

    @PostMapping("/items")
    public ResponseEntity<CartAddResponse> addItemToCart(@RequestBody CartAddRequest request,
                                                         @CookieValue(value = "guestCookie", required = false) String guestId,
                                                         @AuthenticationPrincipal MemberPrincipal principal,
                                                         HttpServletResponse httpResponse) {

        Long memberId = (principal != null) ? principal.getMemberId() : null;

        if (memberId == null && guestId == null) {
            guestId = UUID.randomUUID().toString();
            CookieUtils.addCookie(httpResponse, "guestCookie", guestId, 60 * 60 * 24 * 7);
        }

        CartAddResponse response = cartService.addToCart(request, memberId, guestId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 장바구니가 없으면 그냥 빈 리스트 반환
    @GetMapping
    public ResponseEntity<CartListResponse> getCartItems(@CookieValue(value = "guestCookie", required = false) String guestId,
                                                         @AuthenticationPrincipal MemberPrincipal principal,
                                                         Pageable pageable){
        Long memberId = (principal != null) ? principal.getMemberId() : null;

        CartListResponse cartList = cartService.getCartItemList(memberId, guestId);

       return ResponseEntity.ok(cartList);
    }

    // 장바구니 비우기
    @DeleteMapping("/items")
    public ResponseEntity<Void> deleteAllCartItem(@CookieValue(value = "guestCookie", required = false) String guestId,
                                                  @AuthenticationPrincipal MemberPrincipal principal){
        Long memberId = (principal != null) ? principal.getMemberId() : null;

        cartService.deleteAllCartItem(memberId, guestId);

        return ResponseEntity.noContent().build();
    }

    // 수량 변경, 책의 아이디와 바뀔 수량은 request에 담겨서 넘어옴
    @PutMapping("/items")
    public ResponseEntity<CartUpdateResponse> updateQuantity(@RequestBody @Valid CartItemUpdateRequest request,
                                                             @AuthenticationPrincipal MemberPrincipal principal,
                                                             @CookieValue(value = "guestCookie", required = false) String guestId) {
        Long memberId = (principal != null) ? principal.getMemberId() : null;

        CartUpdateResponse response = cartService.updateCartItemQuantity(memberId, guestId, request);
        return ResponseEntity.ok(response);
    }

    // 책 단건 삭제
    @DeleteMapping("/items/{bookId}")
    public ResponseEntity<Void> deleteOneItem(@PathVariable Long bookId,
                                              @AuthenticationPrincipal MemberPrincipal principal,
                                              @CookieValue(value = "guestCookie", required = false) String guestId) {
        Long memberId = (principal != null) ? principal.getMemberId() : null;

        cartService.deleteCartItem(memberId, guestId, bookId);

        return ResponseEntity.noContent().build();
    }

    // 장바구니 합친다 했을때 예
    @PostMapping("/merge")
    public ResponseEntity<Void> mergeGuestCart(@AuthenticationPrincipal MemberPrincipal principal,
                                               @CookieValue(value = "guestCookie", required = false) String guestId,
                                               HttpServletResponse response){
        Long memberId = (principal != null) ? principal.getMemberId() : null;

        if(guestId != null){
            cartService.migrateGuestCart(guestId, memberId);
            CookieUtils.deleteCookie(response, "guestCookie");
        }

        return ResponseEntity.ok().build();
    }

    // 장바구니를 합치겠습니까? 했을 때 아니오
    @DeleteMapping("/guest")
    public ResponseEntity<Void> ignoreGuestCart(@CookieValue(value = "guestCookie", required = false) String guestId,
                                                HttpServletResponse response) {
        if (guestId != null) {
            // 1. Redis 비회원 키 삭제
            cartService.deleteGuestCartOnly(guestId);

            // 2. 쿠키 삭제
            CookieUtils.deleteCookie(response, "guestCookie");
        }
        return ResponseEntity.noContent().build();
    }



}
