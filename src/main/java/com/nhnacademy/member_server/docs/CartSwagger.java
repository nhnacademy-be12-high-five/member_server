package com.nhnacademy.member_server.docs;

import com.nhnacademy.member_server.dto.cartRequest.CartAddRequest;
import com.nhnacademy.member_server.dto.cartRequest.CartItemUpdateRequest;
import com.nhnacademy.member_server.dto.cartResponse.CartAddResponse;
import com.nhnacademy.member_server.dto.cartResponse.CartListResponse;
import com.nhnacademy.member_server.dto.cartResponse.CartUpdateResponse;
import com.nhnacademy.member_server.entity.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Cart API", description = "장바구니(회원/비회원) 관련 API")
public interface CartSwagger {

    @Operation(summary = "장바구니에 책 추가", description = "사용자의 장바구니를 찾아 책을 추가하는 기능입니다")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "장바구니 담기 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (수량 오류 등)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 책 (Book Service)")
    })
    @PostMapping("/items")
    ResponseEntity<CartAddResponse> addItemToCart(@RequestBody CartAddRequest request, @CookieValue(value = "guestCookie", required = false) String guestId, @AuthenticationPrincipal MemberPrincipal principal, HttpServletResponse httpResponse);


    @Operation(summary = "장바구니 내역 조회", description = "장바구니에 담긴 책의 기본 정보들을 보여주는 기능입니다")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "503", description = "도서 서비스(Book-Service) 연결 실패")
    })
    @GetMapping
    ResponseEntity<CartListResponse> getCartItems(@CookieValue(value = "guestCookie", required = false) String guestId, @AuthenticationPrincipal MemberPrincipal principal,
                                                  Pageable pageable);


    @Operation(summary = "장바구니 전체 비우기", description = "장바구니에 담긴 모든 상품을 삭제합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "삭제 성공 (No Content)"),
            @ApiResponse(responseCode = "404", description = "장바구니를 찾을 수 없음")
    })
    @DeleteMapping("/items")
    ResponseEntity<Void> deleteAllCartItem(@CookieValue(value = "guestCookie", required = false) String guestId, @AuthenticationPrincipal MemberPrincipal principal);


    @Operation(summary = "장바구니 수량 변경", description = "장바구니에 담긴 상품의 수량을 변경합니다. (최소 1개)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "수량 변경 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 수량 (1 미만)"),
            @ApiResponse(responseCode = "404", description = "장바구니나 상품을 찾을 수 없음")
    })
    @PutMapping("/items")
    ResponseEntity<CartUpdateResponse> updateQuantity(@RequestBody @Valid CartItemUpdateRequest request, @AuthenticationPrincipal MemberPrincipal principal, @CookieValue(value = "guestCookie", required = false) String guestId);


    @Operation(summary = "장바구니 상품 단건 삭제", description = "장바구니에서 특정 책 하나를 삭제합니다.")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "삭제 성공")})
    @DeleteMapping("/items/{bookId}")
    ResponseEntity<Void> deleteOneItem(@PathVariable Long bookId,  @AuthenticationPrincipal MemberPrincipal principal,@CookieValue(value = "guestCookie", required = false) String guestId);


    @PostMapping("/merge")
    ResponseEntity<Void> mergeGuestCart(@AuthenticationPrincipal MemberPrincipal principal,
                                               @CookieValue(value = "guestCookie", required = false) String guestId,
                                               HttpServletResponse response);


    @DeleteMapping("/guest")
    ResponseEntity<Void> ignoreGuestCart(@CookieValue(value = "guestCookie", required = false) String guestId,
                                                HttpServletResponse response);
}
