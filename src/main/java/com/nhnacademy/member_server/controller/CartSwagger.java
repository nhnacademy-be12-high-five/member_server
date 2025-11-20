package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.dto.CartAddRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

public interface CartSwagger {

    @Operation(summary = "장바구니에 책 추가", description = "사용자의 장바구니를 찾아 책을 추가하는 기능입니다")
    @ApiResponse(responseCode = "201", description = "책 추가 성공")
    @PostMapping("/items")
    ResponseEntity<Void> add(@RequestBody CartAddRequest request, HttpServletRequest httpRequest,HttpServletResponse httpResponse);
}
