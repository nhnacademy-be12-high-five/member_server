package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.dto.CartAddRequest;
import com.nhnacademy.member_server.dto.CartListResponse;
import com.nhnacademy.member_server.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController implements CartSwagger {

    private final CartService cartService;

    @PostMapping("/items")
    public ResponseEntity<Void> add(@RequestBody CartAddRequest request,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse httpResponse){
        cartService.addBookToCart(request, httpRequest, httpResponse);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<CartListResponse> getCartItems(HttpServletRequest httpRequest){
        CartListResponse cartList = cartService.getCartItemList(httpRequest);
       return ResponseEntity.status(200).body(cartList);
    }
}
