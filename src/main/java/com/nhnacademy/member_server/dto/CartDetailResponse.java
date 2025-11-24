package com.nhnacademy.member_server.dto;

public record CartDetailResponse(Long id,
                                 String title,
                                 String author,
                                 Long price,
                                 int quantity,
                                 Long totalPrice,
                                 String image) {}
// 제목 저자 가격 사진 수량 총가격