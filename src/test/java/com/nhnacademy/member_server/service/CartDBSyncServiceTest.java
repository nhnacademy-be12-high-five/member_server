package com.nhnacademy.member_server.service;

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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartDBSyncServiceTest {

    @InjectMocks
    private CartDBSyncService cartDBSyncService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private CartService cartService;

    @Mock
    private SetOperations<String, Object> setOperations;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    @DisplayName("스케줄러 - Dirty Key가 있으면 동기화 수행")
    void syncRedisToDb_Success() {
        // given
        // Dirty Set에서 회원 ID "1"을 꺼냄
        given(setOperations.pop(eq("cart:dirty"), anyLong())).willReturn(List.of("1"));

        // 해당 회원의 장바구니 아이템 조회 Mock
        Map<Object, Object> redisItems = Map.of("100", "2");
        given(hashOperations.entries("cart:m:1")).willReturn(redisItems);

        // when
        cartDBSyncService.syncRedisToDb();

        // then
        verify(cartService).syncToDb(1L, redisItems); // 동기화 메서드 호출 확인
    }

    @Test
    @DisplayName("스케줄러 - 동기화 중 에러 발생 시 Dirty Key 다시 추가")
    void syncRedisToDb_Exception() {
        // given
        given(setOperations.pop(eq("cart:dirty"), anyLong())).willReturn(List.of("1"));
        given(hashOperations.entries("cart:m:1")).willReturn(Map.of("100", "2"));
        // Service 메서드 호출 시 예외 발생
        doThrow(new RuntimeException("DB Error")).when(cartService).syncToDb(anyLong(), anyMap());

        // when
        cartDBSyncService.syncRedisToDb();

        // then
        // 예외가 발생했으므로 Dirty Set에 다시 추가되어야 함
        verify(setOperations).add("cart:dirty", "1");
    }

    @Test
    @DisplayName("스케줄러 - 동기화 대상 없음")
    void syncRedisToDb_NoDirtyKeys() {
        // given
        given(setOperations.pop(eq("cart:dirty"), anyLong())).willReturn(Collections.emptyList());

        // when
        cartDBSyncService.syncRedisToDb();

        // then
        verify(cartService, never()).syncToDb(anyLong(), anyMap());
    }
}