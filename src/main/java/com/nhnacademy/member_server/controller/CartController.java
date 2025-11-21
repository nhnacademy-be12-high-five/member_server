package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.dto.CartAddRequest;
import com.nhnacademy.member_server.dto.CartItemUpdateRequest;
import com.nhnacademy.member_server.dto.CartListResponse;
import com.nhnacademy.member_server.service.CartService;
import com.nhnacademy.member_server.utils.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController implements CartSwagger {

    private final CartService cartService;

    @PostMapping("/items")
    public ResponseEntity<Void> add(@RequestBody CartAddRequest request,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse httpResponse){
        Long memberId = getMemberId();

        String guestId = CookieUtils.getCookieValue(httpRequest, "guestCookie").orElse(null);

        // 비회원이고 카트가 만들어진적이 없다면 새로운 cartId를 리턴해줌
        String newGuestId = cartService.addBookToCart(request, memberId, guestId);

        // 여기서 cartId를 쿠키에 저장해주는 것
        if(newGuestId != null){
            CookieUtils.addCookie(httpResponse, "guestCookie", newGuestId, 60*60*24*30);
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<CartListResponse> getCartItems(HttpServletRequest httpRequest){
        Long memberId = getMemberId();
        String guestId = CookieUtils.getCookieValue(httpRequest, "guestCookie").orElse(null);

        CartListResponse cartList = cartService.getCartItemList(memberId, guestId);
       return ResponseEntity.status(200).body(cartList);
    }

    // 장바구니 비우기
    @DeleteMapping("/items")
    public ResponseEntity<Void> deleteAllCartItem(HttpServletRequest httpRequest,
                                               HttpServletResponse httpResponse){
        Long memberId = getMemberId();
        String guestId = CookieUtils.getCookieValue(httpRequest, "guestCookie").orElse(null);

        cartService.deleteAllCartItem(memberId, guestId);
        return ResponseEntity.noContent().build();
    }

    // 수량 변경, 책의 아이디와 바뀔 수량은 request에 담겨서 넘어옴
    @PutMapping("/items")
    public ResponseEntity<Void> updateQuantity(@RequestBody @Valid CartItemUpdateRequest request,
                                               HttpServletRequest httpRequest) {
        Long memberId = getMemberId();
        String guestId = CookieUtils.getCookieValue(httpRequest, "guestCookie").orElse(null);

        cartService.updateCartItemQuantity(memberId, guestId, request);
        return ResponseEntity.ok().build();
    }

    // 책 단건 삭제
    @DeleteMapping("/items/{bookId}")
    public ResponseEntity<Void> deleteOneItem(@PathVariable Long bookId,
                                              HttpServletRequest httpRequest) {
        Long memberId = getMemberId();
        String guestId = CookieUtils.getCookieValue(httpRequest, "guestCookie").orElse(null);

        cartService.deleteCartItem(memberId, guestId, bookId);
        return ResponseEntity.noContent().build();
    }



    // 멤버 아이디를 가져오는 함수입니다. 인증을 거쳐서 들어오는거라 없으면 null 값을 반환합니다.
    private Long getMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            String userId = ((UserDetails) auth.getPrincipal()).getUsername();
            return Long.parseLong(userId);
        }
        return null;
    }
}
