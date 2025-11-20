package com.nhnacademy.member_server.cache;

import com.nhnacademy.member_server.dto.CartDetailResponse;
import com.nhnacademy.member_server.feign.BookFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
public class BookCacheService {

    private final BookFeignClient bookFeignClient;

    @Cacheable(value = "book", key = "#bookId", cacheManager = "cacheManager")
    public CartDetailResponse getBook(Long bookId) {
        return bookFeignClient.getBook(bookId);
    }

    @Cacheable(value = "bookBulk", key = "#bookIds.hashCode()", cacheManager = "cacheManager")
    public List<CartDetailResponse> getBooksBulk(List<Long> bookIds) {
        return bookFeignClient.getBooksBulk(bookIds);
    }
}
