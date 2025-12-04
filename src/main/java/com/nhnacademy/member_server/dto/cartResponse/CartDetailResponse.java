package com.nhnacademy.member_server.dto.cartResponse;

public record CartDetailResponse(Long bookId,
                                 String title,
                                 Integer price,
                                 int quantity,
                                 Integer totalPrice,
                                 String image) {}
// 제목 저자 가격 사진 수량 총가격
