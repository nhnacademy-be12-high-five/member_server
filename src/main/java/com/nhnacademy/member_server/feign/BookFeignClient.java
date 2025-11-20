package com.nhnacademy.member_server.feign;

import com.nhnacademy.member_server.dto.CartDetailResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

// 도서 쪽에서 구현하면 책 정보 가져오기

@FeignClient(name = "book-service")
public interface BookFeignClient {
    @GetMapping("/books/{bookId}")
    CartDetailResponse getBook(@PathVariable("bookId") Long bookId);

    @PostMapping("/books/bulk")
    List<CartDetailResponse> getBooksBulk(@RequestBody List<Long> bookIds);
}
