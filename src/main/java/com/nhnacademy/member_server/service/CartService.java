package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.dto.CartAddRequest;
import com.nhnacademy.member_server.dto.CartDetailResponse;
import com.nhnacademy.member_server.dto.CartListResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

public interface CartService {
    CartListResponse getCartItemList(HttpServletRequest httpServletRequest);
    void addBookToCart(CartAddRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse);
}
