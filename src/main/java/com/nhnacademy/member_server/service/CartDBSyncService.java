package com.nhnacademy.member_server.service;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartDBSyncService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CartService cartService;

    private static final String DIRTY_KEY = "cart:dirty";
    private static final int MAX_SYNC_BATCH_SIZE = 1000;

    @Scheduled(fixedDelay = 6_000)
    public void syncRedisToDb() {

        List<Object> memberIdStr = redisTemplate.opsForSet().pop(DIRTY_KEY, MAX_SYNC_BATCH_SIZE);

        if (memberIdStr != null) {
            for (Object memberId : memberIdStr) {
                try {
                    // Redis 조회는 트랜잭션 필요 없음 (빠르게 조회)
                    String redisKey = "cart:m:" + memberId;
                    Map<Object, Object> redisItems = redisTemplate.opsForHash().entries(redisKey);

                    // 한 명 동기화가 끝나면 바로 커밋됨.
                    cartService.syncToDb(Long.parseLong((String)memberId), redisItems);

                } catch (Exception e) {
                    log.error("Sync failed for member: {}", memberId, e);
                    // 실패 시 다시 큐에 넣음 -> 이것이 단독으로 Transactional 걸어서 가능한 것
                    redisTemplate.opsForSet().add(DIRTY_KEY, memberId);
                }
            }
        }
    }
}
