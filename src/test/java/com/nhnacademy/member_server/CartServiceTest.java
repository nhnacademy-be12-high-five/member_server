package com.nhnacademy.member_server;

import com.nhnacademy.member_server.dto.cartRequest.CartAddRequest;
import com.nhnacademy.member_server.service.impl.CartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @InjectMocks
    private CartServiceImpl cartService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    // RedisTemplate.opsForHash() 등이 반환하는 객체들을 Mocking 해야 함
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private SetOperations<String, Object> setOperations;

    @BeforeEach
    void setUp() {
        // redisTemplate.opsForHash()를 호출하면 -> 가짜 hashOperations를 줘라
        given(redisTemplate.opsForHash()).willReturn(hashOperations);

        // redisTemplate.opsForSet()을 호출하면 -> 가짜 setOperations를 줘라
        given(redisTemplate.opsForSet()).willReturn(setOperations);
    }

    @Test
    @DisplayName("장바구니 담기 - 회원인 경우 Dirty Set 추가 확인")
    void addToCart_member() {
        // Given
        Long memberId = 1L;
        String guestId = null;
        CartAddRequest request = new CartAddRequest(100L, 2); // 책 100번, 2권

        // 현재 수량 조회 시 null (처음 담음)
        given(hashOperations.get(anyString(), anyString())).willReturn(null);

        // When
        cartService.addToCart(request, memberId, guestId);

        // Then
        String key = "cart:m:1";
        // 1. Redis Hash에 저장되었는지? (수량 2)
        verify(hashOperations).put(key, "100", "2");
        // 2. 만료시간 30일 설정되었는지?
        verify(redisTemplate).expire(key, 30, TimeUnit.DAYS);
        // 3. ⭐ 회원이니까 Dirty Set(DB 동기화 목록)에 추가되었는지?
        verify(setOperations).add("cart:dirty", "1");
    }

    @Test
    @DisplayName("비회원 장바구니 병합 (migrateGuestCart)")
    void migrateGuestCart() {
        // Given
        String guestId = "guest-123";
        Long memberId = 10L;

        String guestKey = "cart:g:" + guestId;
        String memberKey = "cart:m:" + memberId;

        // 비회원 장바구니 데이터 준비
        Map<Object, Object> guestData = Map.of("100", "1", "200", "2");

        // Redis에 guestKey가 있다고 가정
        given(redisTemplate.hasKey(guestKey)).willReturn(true);
        // Redis에서 guest 데이터 꺼내기 Mocking
        given(hashOperations.entries(guestKey)).willReturn(guestData);

        // When
        cartService.migrateGuestCart(guestId, memberId);

        // Then
        // 1. 회원 키로 데이터가 복사(putAll) 되었는지?
        verify(hashOperations).putAll(memberKey, guestData);

        // 2. 비회원 키는 삭제되었는지?
        verify(redisTemplate).delete(guestKey);

        // 3. 회원이니까 Dirty Set에 추가되었는지?
        verify(setOperations).add("cart:dirty", String.valueOf(memberId));

        // 4. 회원 키 만료시간 갱신되었는지?
        verify(redisTemplate).expire(memberKey, 30, TimeUnit.DAYS);
    }
}