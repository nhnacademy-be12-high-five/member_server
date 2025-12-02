package com.nhnacademy.member_server.feign;

import java.util.List;

import com.nhnacademy.member_server.dto.cartResponse.CartDetailResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// 도서 쪽에서 구현하면 책 정보 가져오기

@FeignClient(name = "book-service")
public interface BookFeignClient {

    @PostMapping("/books/bulk")
    List<CartDetailResponse> getBooksBulk(@RequestBody List<Long> bookIds);
}
