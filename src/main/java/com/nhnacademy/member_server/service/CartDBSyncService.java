package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.entity.cartEntity.Cart;
import com.nhnacademy.member_server.entity.cartEntity.CartItem;
import com.nhnacademy.member_server.repository.CartItemRepository;
import com.nhnacademy.member_server.repository.CartRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final String DIRTY_KEY = "cart:dirty"; // 변경된 회원 ID 목록

    @Scheduled(fixedDelay = 60_000) // 앞타임 끝나고 1분마다
    @Transactional
    public void syncRedisToDb() {

        // 변경된 회원들만 꺼내서 작업을 진행함 -> 성능 끝판왕
        Set<Object> dirtyMemberIds = redisTemplate.opsForSet().members(DIRTY_KEY);

        // 없으면 조기 퇴근
        if (dirtyMemberIds == null || dirtyMemberIds.isEmpty()) {
            return;
        }

        // 작업 시작
        for (Object idObj : dirtyMemberIds) {
            String memberIdStr = (String) idObj;
            Long memberId = Long.parseLong(memberIdStr);
            String redisKey = "cart:m:" + memberId;

            // Redis 해당 회원의 장바구니 조회
            Map<Object, Object> redisItems = redisTemplate.opsForHash().entries(redisKey);

            // DB 동기화 (기존 것 지우고 새로 쓰기)
            Cart cart = cartRepository.findByMember_Id(memberId)
                    .orElseGet(() -> cartRepository.save(new Cart(memberRepository.getReferenceById(memberId))));

            cartItemRepository.deleteByCartId(cart.getId()); // 기존 DB 데이터 삭제 (DELETE)

            // INSERT
            if (!redisItems.isEmpty()) {
                redisItems.forEach((bookIdStr, qtyStr) -> {
                    cartItemRepository.save(new CartItem(
                            Long.parseLong((String)bookIdStr),
                            Integer.parseInt((String)qtyStr),
                            cart
                    ));
                });
            }

            // 처리 완료했으니 Dirty Set에서 제거
            redisTemplate.opsForSet().remove(DIRTY_KEY, memberIdStr);
        }
    }
}
