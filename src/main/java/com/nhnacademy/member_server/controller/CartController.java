package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.docs.CartSwagger;
import com.nhnacademy.member_server.dto.cartRequest.CartAddRequest;
import com.nhnacademy.member_server.dto.cartRequest.CartItemUpdateRequest;
import com.nhnacademy.member_server.dto.cartResponse.CartAddResponse;
import com.nhnacademy.member_server.dto.cartResponse.CartListResponse;
import com.nhnacademy.member_server.entity.MemberPrincipal;
import com.nhnacademy.member_server.service.CartService;
import com.nhnacademy.member_server.utils.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController implements CartSwagger {

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
        Long memberId = principal.getMemberId();

        CartListResponse cartList = cartService.getCartItemList(memberId, guestId);

       return ResponseEntity.ok(cartList);
    }

    // 장바구니 비우기
    @DeleteMapping("/items")
    public ResponseEntity<Void> deleteAllCartItem(HttpServletRequest httpRequest,
                                                  @AuthenticationPrincipal MemberPrincipal principal,
                                               HttpServletResponse httpResponse){
        Long memberId = principal.getMemberId();
        String guestId = CookieUtils.getCookieValue(httpRequest, "guestCookie").orElse(null);

        cartService.deleteAllCartItem(memberId, guestId);
        return ResponseEntity.noContent().build();
    }

    // 수량 변경, 책의 아이디와 바뀔 수량은 request에 담겨서 넘어옴
    @PutMapping("/items")
    public ResponseEntity<Void> updateQuantity(@RequestBody @Valid CartItemUpdateRequest request,
                                               @AuthenticationPrincipal MemberPrincipal principal,
                                               HttpServletRequest httpRequest) {
        Long memberId = principal.getMemberId();
        String guestId = CookieUtils.getCookieValue(httpRequest, "guestCookie").orElse(null);

        cartService.updateCartItemQuantity(memberId, guestId, request);
        return ResponseEntity.ok().build();
    }

    // 책 단건 삭제
    @DeleteMapping("/items/{bookId}")
    public ResponseEntity<Void> deleteOneItem(@PathVariable Long bookId,
                                              @AuthenticationPrincipal MemberPrincipal principal,
                                              HttpServletRequest httpRequest) {
        Long memberId = principal.getMemberId();
        String guestId = CookieUtils.getCookieValue(httpRequest, "guestCookie").orElse(null);

        cartService.deleteCartItem(memberId, guestId, bookId);
        return ResponseEntity.noContent().build();
    }
}
