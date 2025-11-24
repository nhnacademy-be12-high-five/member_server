package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.entity.Cart;
import com.nhnacademy.member_server.entity.CartItem;
import com.nhnacademy.member_server.repository.CartItemRepository;
import com.nhnacademy.member_server.repository.CartRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.repository.RedisCartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CartDBSyncService {

    private final RedisCartRepository redisCartRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final MemberRepository memberRepository;

    // 1분마다 실행 (운영에서는 주기 조정 / scan 기반으로 변경 권장)
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void syncRedisToDb() {
        Set<String> keys = redisCartRepository.getAllCartKeys();
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            // key 포맷: cart:m:{memberId} 또는 cart:g:{guestId}
            if (key.startsWith("cart:m:")) {
                String memberIdStr = key.substring("cart:m:".length());
                Long memberId = Long.parseLong(memberIdStr);

                Map<Long, Integer> items = redisCartRepository.getCartItems(key);

                // 회원 카트 동기화: 간단하게 기존 항목 다 지우고 다시 생성
                Cart cart = cartRepository.findByMember_Id(memberId)
                        .orElseGet(() -> cartRepository.save(new Cart(memberRepository.getReferenceById(memberId)))); // create cart with member ref

                // delete existing items (단순 구현; 최적화 가능)
                cartItemRepository.deleteByCartId(cart.getId());

                items.forEach((bookId, qty) -> {
                    cartItemRepository.save(new CartItem(bookId, qty, cart));
                });
            } else {
                // guest 카트: 정책에 따라 처리 (예: DB에 저장하지 않음 또는 별도 로직)
                // 현재는 스킵
            }
        }
    }

    // 주문 직전 단건 즉시 저장: member 전용 helper
    @Transactional
    public void saveMemberCartNow(Long memberId, Map<Long, Integer> items) {
        Cart cart = cartRepository.findByMember_Id(memberId)
                .orElseGet(() -> cartRepository.save(new Cart(memberRepository.getReferenceById(memberId))));

        cartItemRepository.deleteByCartId(cart.getId());
        items.forEach((bookId, qty) -> cartItemRepository.save(new CartItem(bookId, qty, cart)));
    }
}
