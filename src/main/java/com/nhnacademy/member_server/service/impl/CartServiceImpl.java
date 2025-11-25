package com.nhnacademy.member_server.service.impl;

import com.nhnacademy.member_server.dto.cartRequest.CartAddRequest;
import com.nhnacademy.member_server.dto.cartRequest.CartItemUpdateRequest;
import com.nhnacademy.member_server.dto.cartResponse.CartDetailResponse;
import com.nhnacademy.member_server.dto.cartResponse.CartListResponse;
import com.nhnacademy.member_server.exception.BusinessException;
import com.nhnacademy.member_server.exception.ErrorCode;
import com.nhnacademy.member_server.feign.BookFeignClient;
import com.nhnacademy.member_server.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final BookFeignClient bookFeignClient;

    private static final String DIRTY_KEY = "cart:dirty"; // DB 동기화 대상 목록

    // Key 생성 헬퍼 메서드
    private String getRedisKey(Long memberId, String guestId) {
        if (memberId != null) {
            return "cart:m:" + memberId;
        }
        return "cart:g:" + guestId;
    }

    // 장바구니 리스트 조회
    @Override
    public CartListResponse getCartItemList(Long memberId, String guestId) {
        String key = getRedisKey(memberId, guestId);

        // 상품 가져오기 bookId : quantity
        Map<Object, Object> redisItems = redisTemplate.opsForHash().entries(key);

        // 비어있으면 빈 리스트 반환
        if (redisItems.isEmpty()) {
            return new CartListResponse(Collections.emptyList(), 0L);
        }

        // 책 ID 리스트 추출
        List<Long> bookIds = redisItems.keySet().stream()
                .map(k -> Long.valueOf((String) k))
                .toList();

        // Feign과 bookId로 책 정보 Bulk 조회
        List<CartDetailResponse> bookInfoList = bookFeignClient.getBooksBulk(bookIds);
        // 검색 쉽게 하기 위해 map 으로 바꿈
        Map<Long, CartDetailResponse> bookMap = bookInfoList.stream()
                .collect(Collectors.toMap(CartDetailResponse::bookId, b -> b));

        // Redis 수량 + 책 정보 합치기
        List<CartDetailResponse> responseList = new ArrayList<>();
        long totalCartPrice = 0L;

        for (Long bookId : bookIds) {
            CartDetailResponse book = bookMap.get(bookId);
            if (book == null) continue; // 책 정보가 없으면 스킵 (혹은 삭제 처리)

            // redis에 담긴 bookId에 해당하는 수량 꺼내기
            int quantity = Integer.parseInt((String) redisItems.get(String.valueOf(bookId)));

            long itemTotalPrice = book.price() * quantity;
            totalCartPrice += itemTotalPrice;

            responseList.add(new CartDetailResponse(
                    book.bookId(), book.title(), book.author(), book.price(),
                    quantity, itemTotalPrice, book.image()
            ));
        }

        return new CartListResponse(responseList, totalCartPrice);
    }

    // 장바구니 담기
    @Override
    public void addToCart(CartAddRequest request, Long memberId, String guestId) {
        String key = getRedisKey(memberId, guestId);
        String bookIdStr = String.valueOf(request.bookId());

        // 해당하는 책이 있으면 request.quantity 만큼 증가, 없으면 알아서 만들어서 증가
        redisTemplate.opsForHash().increment(key, bookIdStr, request.quantity());

        // 만료 시간 설정 (회원/비회원 모두 30일 뒤 자동 삭제 - 갱신됨)
        redisTemplate.expire(key, 30, TimeUnit.DAYS);

        // db를 업데이트해야하는 멤버 아이디를 알려줌
        if (memberId != null) {
            redisTemplate.opsForSet().add(DIRTY_KEY, String.valueOf(memberId));
        }
    }

    // 수량 변경
    @Override
    public void updateCartItemQuantity(Long memberId, String guestId, CartItemUpdateRequest request) {
        String key = getRedisKey(memberId, guestId);
        String bookIdStr = String.valueOf(request.bookId());

        if (!redisTemplate.opsForHash().hasKey(key, bookIdStr)) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        // 덮어쓰기
        redisTemplate.opsForHash().put(key, bookIdStr, String.valueOf(request.quantity()));

        if (memberId != null) {
            redisTemplate.opsForSet().add(DIRTY_KEY, String.valueOf(memberId));
        }
    }

    // 책 자체를 삭제
    @Override
    public void deleteCartItem(Long memberId, String guestId, Long bookId) {
        String key = getRedisKey(memberId, guestId);

        redisTemplate.opsForHash().delete(key, String.valueOf(bookId));

        if (memberId != null) {
            redisTemplate.opsForSet().add(DIRTY_KEY, String.valueOf(memberId));
        }
    }

    // 장바구니 비우기
    @Override
    public void deleteAllCartItem(Long memberId, String guestId) {
        String key = getRedisKey(memberId, guestId);

        redisTemplate.delete(key); // Key 자체를 삭제

        if (memberId != null) {
            redisTemplate.opsForSet().add(DIRTY_KEY, String.valueOf(memberId));
        }
    }

    @Override
    public void migrateGuestCart(String guestId, Long memberId) {
        String guestKey = "cart:g:" + guestId;
        String memberKey = "cart:m:" + memberId;
        String dirtyKey = "cart:dirty";

        if (redisTemplate.hasKey(guestKey)) {
            Map<Object, Object> guestItems = redisTemplate.opsForHash().entries(guestKey);

            // 회원 장바구니에 덮어쓰기 (putAll)
            redisTemplate.opsForHash().putAll(memberKey, guestItems);

            redisTemplate.delete(guestKey);

            redisTemplate.opsForSet().add(dirtyKey, String.valueOf(memberId));

            redisTemplate.expire(memberKey, 30, TimeUnit.DAYS);
        }
    }
}