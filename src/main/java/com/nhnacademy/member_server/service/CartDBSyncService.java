package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.entity.cartEntity.Cart;
import com.nhnacademy.member_server.entity.cartEntity.CartItem;
import com.nhnacademy.member_server.repository.CartItemRepository;
import com.nhnacademy.member_server.repository.CartRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartDBSyncService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final MemberRepository memberRepository;

    private static final String DIRTY_KEY = "cart:dirty";

    // 1. 시간 간격을 5분(300초)으로 늘림 -> DB 부하 1/5로 감소
    @Scheduled(fixedDelay = 300_000)
    public void syncRedisToDb() {
        processSync();
    }

    // 2. 서버가 정상 종료될 때 마지막으로 한 번 싹 저장함 (안전장치)
    @PreDestroy
    public void onShutdown() {
        log.info("Server shutting down. Syncing cart data to DB...");
        processSync();
    }

    @Transactional
    public void processSync() {
        // [핵심 개선] members()로 다 가져오지 말고, pop()으로 하나씩 꺼내서 처리
        // 이렇게 하면 처리 중에 다른 회원이 추가되어도 큐에 남아있거나 다시 들어오므로 안전함
        // Redis Set에서 하나를 꺼냄 (꺼내는 순간 Set에서 사라짐)
        String memberIdStr = (String) redisTemplate.opsForSet().pop(DIRTY_KEY);

        while (memberIdStr != null) {
            Long memberId = Long.parseLong(memberIdStr);
            syncMemberCart(memberId); // 실제 동기화 로직 분리

            // 다음 회원 꺼내기 (없으면 null 반환되어 루프 종료)
            memberIdStr = (String) redisTemplate.opsForSet().pop(DIRTY_KEY);
        }
    }

    private void syncMemberCart(Long memberId) {
        try {
            String redisKey = "cart:m:" + memberId;
            Map<Object, Object> redisItems = redisTemplate.opsForHash().entries(redisKey);

            // [방어 로직] Redis 데이터가 만료되거나 비어있으면 DB 건드리지 않음 (혹은 삭제 정책에 따라 다름)
            if (redisItems.isEmpty()) {
                return;
            }

            Cart cart = cartRepository.findByMember_Id(memberId)
                    .orElseGet(() -> cartRepository.save(new Cart(memberRepository.getReferenceById(memberId))));

            // Delete & Insert 방식 유지 (구현 복잡도를 낮추기 위해 장바구니에선 이 방식이 효율적임)
            // 단, JPA의 1+N 삭제 문제를 방지하기 위해 JPQL로 한방 쿼리 작성 추천
            cartItemRepository.deleteAllByCartId(cart.getId());

            // Bulk Insert (JDBC Batch 등을 쓰면 더 좋지만, saveAll도 괜찮음)
            List<CartItem> newItems = redisItems.entrySet().stream()
                    .map(entry -> new CartItem(
                            Long.parseLong((String) entry.getKey()),
                            Integer.parseInt((String) entry.getValue()),
                            cart
                    )).toList();

            cartItemRepository.saveAll(newItems);

        } catch (Exception e) {
            log.error("Sync failed for member: {}", memberId, e);
            // [실패 복구] DB 저장에 실패하면 Dirty Key에 다시 넣어줘야 다음 텀에 다시 시도함
            redisTemplate.opsForSet().add(DIRTY_KEY, String.valueOf(memberId));
        }
    }
}

