package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.dto.CartAddRequest;
import com.nhnacademy.member_server.dto.CartDetailResponse;
import com.nhnacademy.member_server.dto.CartListResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

public interface CartService {
    CartListResponse getCartItemList(Long memberId, String guestId);

    String addBookToCart(CartAddRequest request, Long memberId, String guestId);

    void deleteCartItem(Long memberId, String guestId);
}