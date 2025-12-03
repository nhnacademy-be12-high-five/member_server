package com.nhnacademy.member_server.dto.cartResponse;

public record GetBookResponse(Long bookId,
                              String title,
                              Integer price,
                              String image) {}