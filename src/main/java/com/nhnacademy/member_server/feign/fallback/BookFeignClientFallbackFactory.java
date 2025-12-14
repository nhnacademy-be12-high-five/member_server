package com.nhnacademy.member_server.feign.fallback;

import com.nhnacademy.member_server.dto.cartResponse.GetBookResponse;
import com.nhnacademy.member_server.feign.BookFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class BookFeignClientFallbackFactory implements FallbackFactory<BookFeignClient> {

    @Override
    public BookFeignClient create(Throwable cause) {
        return new BookFeignClient() {
            @Override
            public List<GetBookResponse> getBooksBulk(List<Long> bookIds) {
                // 에러 로그는 남기되, 사용자에게는 에러를 터뜨리지 않음
                log.error("📚 Book Service 연결 실패 (Fallback 실행). 원인: {}", cause.getMessage());

                // 빈 리스트를 반환하거나, "정보 없음" 객체를 반환할 수 있음
                if (bookIds == null || bookIds.isEmpty()) {
                    return Collections.emptyList();
                }

                // 상품 정보는 없지만 ID와 수량은 보여주기 위해 더미 객체 반환
                return bookIds.stream()
                        .map(id -> new GetBookResponse(id, "상품 정보를 불러올 수 없습니다.", 0, null))
                        .toList();
            }
        };
    }
}