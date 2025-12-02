package com.nhnacademy.member_server.service;

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

    @Scheduled(fixedDelay = 300_000)
    public void syncRedisToDb() {

        String memberIdStr = (String) redisTemplate.opsForSet().pop(DIRTY_KEY);

        while (memberIdStr != null) {
            Long memberId = Long.parseLong(memberIdStr);
            try {
                // Redis 조회는 트랜잭션 필요 없음 (빠르게 조회)
                String redisKey = "cart:member:" + memberId;
                Map<Object, Object> redisItems = redisTemplate.opsForHash().entries(redisKey);

                if (!redisItems.isEmpty()) {
                    // 한 명 동기화가 끝나면 바로 커밋됨.
                    cartService.syncToDb(memberId, redisItems);
                }
            } catch (Exception e) {
                log.error("Sync failed for member: {}", memberId, e);
                // 실패 시 다시 큐에 넣음 -> 이것이 단독으로 Transactional 걸어서 가능한 것
                redisTemplate.opsForSet().add(DIRTY_KEY, memberIdStr);
            }

            // 다음 사람
            memberIdStr = (String) redisTemplate.opsForSet().pop(DIRTY_KEY);
        }
    }
}
