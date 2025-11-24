package com.nhnacademy.member_server.repository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RedisCartRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    // HashOperations은 RedisTemplate.opsForHash()로 얻음 (hash key / field key 모두 문자열로 다루는편이 안전)
    private HashOperations<String, String, Integer> hashOps;

    @PostConstruct
    public void init() {
        this.hashOps = redisTemplate.opsForHash();
    }

    // cart key 생성: member인 경우 "cart:m:{memberId}", 비회원 guestId인 경우 "cart:g:{guestId}"
    public String getCartKey(Long memberId, String guestId) {
        if (memberId != null) {
            return "cart:m:" + memberId;
        } else {
            return "cart:g:" + guestId; // guestId는 UUID 문자열 권장
        }
    }

    // 모든 항목을 Map<Long, Integer> 형태로 반환 (hash의 field는 bookId 문자열로 저장)
    public Map<Long, Integer> getCartItems(String cartKey) {
        Map<String, Integer> raw = hashOps.entries(cartKey);
        return raw.entrySet().stream()
                .collect(Collectors.toMap(e -> Long.parseLong(e.getKey()), Map.Entry::getValue));
    }

    // 개별 수량 설정 (overwrite)
    public void updateQuantity(String cartKey, Long bookId, Integer quantity) {
        hashOps.put(cartKey, String.valueOf(bookId), quantity);
    }

    // 수량 증감 (원자적)
    public Long incrementQuantity(String cartKey, Long bookId, long delta) {
        // opsForHash().increment returns Long (note: requires numeric stored)
        return redisTemplate.opsForHash().increment(cartKey, String.valueOf(bookId), delta);
    }

    // 항목 삭제
    public void deleteItem(String cartKey, Long bookId) {
        hashOps.delete(cartKey, String.valueOf(bookId));
    }

    // 카트 전체 삭제
    public void clearCart(String cartKey) {
        redisTemplate.delete(cartKey);
    }

    // 모든 cart 키 조회 (주의: 운영환경에서는 패턴 스캔 비용 고려)
    public Set<String> getAllCartKeys() {
        // RedisTemplate.keys uses pattern; in cluster mode prefer scan
        return redisTemplate.keys("cart:*");
    }

    // 존재 여부
    public boolean exists(String cartKey) {
        return redisTemplate.hasKey(cartKey);
    }
}
