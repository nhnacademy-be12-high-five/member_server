package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.dto.cartRequest.CartAddRequest;
import com.nhnacademy.member_server.dto.cartRequest.CartItemUpdateRequest;
import com.nhnacademy.member_server.dto.cartResponse.CartAddResponse;
import com.nhnacademy.member_server.dto.cartResponse.CartListResponse;
import java.util.Map;

public interface CartService {
    CartListResponse getCartItemList(Long memberId, String guestId);

    CartAddResponse addToCart(CartAddRequest request, Long memberId, String guestId);

    void restoreCartOnLogin(Long memberId);

    void syncToDb(Long memberId, Map<Object, Object> redisItems);

    void deleteAllCartItem(Long memberId, String guestId);

    void deleteCartItem(Long memberId, String guestId, Long bookId);

    void updateCartItemQuantity(Long memberId, String guestId, CartItemUpdateRequest request);

    void migrateGuestCart(String guestId, Long memberId);
}