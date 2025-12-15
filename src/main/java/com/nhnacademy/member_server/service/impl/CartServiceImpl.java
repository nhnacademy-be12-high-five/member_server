package com.nhnacademy.member_server.service.impl;

import com.nhnacademy.member_server.dto.cartRequest.CartAddRequest;
import com.nhnacademy.member_server.dto.cartRequest.CartItemUpdateRequest;
import com.nhnacademy.member_server.dto.cartResponse.*;
import com.nhnacademy.member_server.entity.member.Member;
import com.nhnacademy.member_server.entity.cartEntity.Cart;
import com.nhnacademy.member_server.entity.cartEntity.CartItem;
import com.nhnacademy.member_server.exception.BusinessException;
import com.nhnacademy.member_server.exception.ErrorCode;
import com.nhnacademy.member_server.feign.BookFeignClient;
import com.nhnacademy.member_server.repository.CartItemRepository;
import com.nhnacademy.member_server.repository.CartRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.service.CartService;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final BookFeignClient bookFeignClient;
    private final CartItemRepository cartItemRepository;
    private final CartRepository cartRepository;
    private final MemberRepository memberRepository;
    private static final int MAX_CART_QUANTITY = 100;

    private static final String DIRTY_KEY = "cart:dirty";
    private static final long CART_TTL_DAYS = 7L;

    private String getRedisKey(Long memberId, String guestId) {
        return memberId != null ? "cart:m:" + memberId : "cart:g:" + guestId;
    }

    @Async
    @Transactional
    @Override
    public void restoreCartOnLogin(Long memberId) {
        String key = getRedisKey(memberId, null);
        try {
            loadFromDbAndRestoreToRedis(memberId, key);
        } catch (Exception e) {
            log.error("로그인 시 장바구니 복구 실패 (MemberId: {})", memberId, e);
        }
    }

    @Override
    @Transactional
    public CartAddResponse addToCart(CartAddRequest request, Long memberId, String guestId) {
        String key = getRedisKey(memberId, guestId);
        String bookIdStr = String.valueOf(request.bookId());

        try {
            Object currentVal = redisTemplate.opsForHash().get(key, bookIdStr);
            int currentQuantity = currentVal != null ? Integer.parseInt(String.valueOf(currentVal)) : 0;

            if (currentQuantity + request.quantity() > MAX_CART_QUANTITY) {
                throw new BusinessException(ErrorCode.INVALID_QUANTITY);
            }

            redisTemplate.opsForHash().increment(key, bookIdStr, request.quantity());
            redisTemplate.expire(key, CART_TTL_DAYS, TimeUnit.DAYS);

            if (memberId != null) {
                redisTemplate.opsForSet().add(DIRTY_KEY, String.valueOf(memberId));
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("Redis error during addToCart: {}", e.getMessage());
            throw new BusinessException(ErrorCode.REDIS_SERVER_ERROR);
        }

        return new CartAddResponse(key, request.bookId(), request.quantity());
    }

    @Override
    public CartUpdateResponse updateCartItemQuantity(Long memberId, String guestId, CartItemUpdateRequest request) {
        String key = getRedisKey(memberId, guestId);
        String bookIdStr = String.valueOf(request.bookId());

        try {
            if (!redisTemplate.opsForHash().hasKey(key, bookIdStr)) {
                throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
            }

            redisTemplate.opsForHash().put(key, bookIdStr, String.valueOf(request.quantity()));
            redisTemplate.expire(key, CART_TTL_DAYS, TimeUnit.DAYS);

            if (memberId != null) {
                redisTemplate.opsForSet().add(DIRTY_KEY, String.valueOf(memberId));
            }
        } catch (Exception e) {
            if (e instanceof BusinessException) {
                throw (BusinessException) e;
            }
            log.error("Redis error during updateCartItemQuantity: {}", e.getMessage());
            throw new BusinessException(ErrorCode.REDIS_SERVER_ERROR);
        }

        return new CartUpdateResponse(key, request.bookId(), request.quantity());
    }

    @Override
    public void deleteCartItem(Long memberId, String guestId, Long bookId) {
        String key = getRedisKey(memberId, guestId);
        try {
            redisTemplate.opsForHash().delete(key, String.valueOf(bookId));

            if (memberId != null) {
                redisTemplate.opsForSet().add(DIRTY_KEY, String.valueOf(memberId));
            }
        } catch (Exception e) {
            log.error("Redis error during deleteCartItem: {}", e.getMessage());
            throw new BusinessException(ErrorCode.REDIS_SERVER_ERROR);
        }
    }

    @Override
    public void deleteAllCartItem(Long memberId, String guestId) {
        String key = getRedisKey(memberId, guestId);

        try {
            redisTemplate.delete(key);

            if (memberId != null) {
                redisTemplate.opsForSet().add(DIRTY_KEY, String.valueOf(memberId));
            }
        } catch (Exception e) {
            log.error("Redis error during deleteAllCartItem: {}", e.getMessage());
            throw new BusinessException(ErrorCode.REDIS_SERVER_ERROR);
        }
    }

    @Override
    public void migrateGuestCart(String guestId, Long memberId) {
        String guestKey = "cart:g:" + guestId;
        String memberKey = "cart:m:" + memberId;

        try {
            if (!redisTemplate.hasKey(guestKey)) {
                return;
            }

            Map<Object, Object> guestItems = redisTemplate.opsForHash().entries(guestKey);

            if (!redisTemplate.hasKey(memberKey)) {
                loadFromDbAndRestoreToRedis(memberId, memberKey);
            }

            for (Map.Entry<Object, Object> entry : guestItems.entrySet()) {
                String bookId = (String) entry.getKey();
                int quantity = parseQuantity(entry.getValue());
                if (quantity <= 0) continue;

                Object currentVal = redisTemplate.opsForHash().get(memberKey, bookId);

                int currentQty = currentVal != null ? parseQuantity(currentVal) : 0;
                int newQty = Math.min(currentQty + quantity, MAX_CART_QUANTITY);
                int actualIncrement = newQty - currentQty;

                if (actualIncrement > 0) {
                    redisTemplate.opsForHash().increment(memberKey, bookId, actualIncrement);
                }

            }

            redisTemplate.delete(guestKey);
            redisTemplate.opsForSet().add(DIRTY_KEY, String.valueOf(memberId));
            redisTemplate.expire(memberKey, CART_TTL_DAYS, TimeUnit.DAYS);

        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("Redis error during guest cart migration", e);
            throw new BusinessException(ErrorCode.REDIS_SERVER_ERROR);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncToDb(Long memberId, Map<Object, Object> redisItems) {
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) {
            log.warn("DB Sync failed: Member not found (ID: {})", memberId);
            return;
        }

        Map<Long, Integer> redisMap = new HashMap<>();
        if (redisItems != null) {
            for (Map.Entry<Object, Object> entry : redisItems.entrySet()) {
                try {
                    Long bookId = Long.parseLong(String.valueOf(entry.getKey()));
                    int quantity = Integer.parseInt(String.valueOf(entry.getValue()));
                    redisMap.put(bookId, quantity);
                } catch (NumberFormatException e) {
                    log.warn("Skipped MemberId: {}, Key: {}, Value: {}", memberId, entry.getKey(), entry.getValue());
                }
            }
        }

        Cart cart = cartRepository.findByMember_Id(memberId)
                .orElseGet(() -> cartRepository.save(new Cart(member)));

        List<CartItem> dbItems = cartItemRepository.findByCart_Member_Id(memberId);

        List<CartItem> toDelete = new ArrayList<>();

        for (CartItem dbItem : dbItems) {
            Long bookId = dbItem.getBookId();

            if (redisMap.containsKey(bookId)) {
                int redisQty = redisMap.get(bookId);
                if (dbItem.getQuantity() != redisQty) {
                    dbItem.updateQuantity(redisQty);
                }
                redisMap.remove(bookId);
            } else {
                toDelete.add(dbItem);
            }
            if (!toDelete.isEmpty()) {
                cartItemRepository.deleteAllInBatch(toDelete);
            }
        }

        if (!redisMap.isEmpty()) {
            List<CartItem> newItems = redisMap.entrySet().stream()
                    .map(entry -> new CartItem(entry.getKey(), entry.getValue(), cart))
                    .toList();
            cartItemRepository.saveAll(newItems);
        }
    }

    @Override
    public void deleteGuestCartOnly(String guestId) {
        String key = "cart:g:" + guestId;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Redis error during deleteGuestCartOnly: {}", e.getMessage());
            throw new BusinessException(ErrorCode.REDIS_SERVER_ERROR);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CartListResponse getCartItemList(Long memberId, String guestId) {
        boolean hasGuestCart = false;
        if (memberId == null && guestId == null) {
            return new CartListResponse(Collections.emptyList(), 0L, false);
        }

        String key = getRedisKey(memberId, guestId);

        try {
            Map<Object, Object> redisItems = redisTemplate.opsForHash().entries(key);

            // DB 백업 복구 로직
            if (redisItems.isEmpty() && memberId != null) {
                redisItems = loadFromDbAndRestoreToRedis(memberId, key);
            }

            if (memberId != null && guestId != null) {
                hasGuestCart = redisTemplate.hasKey("cart:g:" + guestId);
            }

            if (redisItems.isEmpty()) {
                return new CartListResponse(Collections.emptyList(), 0L, hasGuestCart);
            }

            redisTemplate.expire(key, CART_TTL_DAYS, TimeUnit.DAYS);

            return calculateCartResponse(redisItems, hasGuestCart, key);

        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("Unexpected error in getCartItemList", e);
            throw new BusinessException(ErrorCode.REDIS_SERVER_ERROR);
        }
    }

    private CartListResponse calculateCartResponse(Map<Object, Object> redisItems, boolean hasGuestCart, String redisKey) {
        List<Long> bookIds = new ArrayList<>();
        Map<Long, Integer> quantityMap = new HashMap<>();

        for (Map.Entry<Object, Object> entry : redisItems.entrySet()) {
            try {
                Long bookId = Long.valueOf(String.valueOf(entry.getKey()));
                int quantity = parseQuantity(entry.getValue());
                if (quantity <= 0) continue;
                bookIds.add(bookId);
                quantityMap.put(bookId, quantity);
            } catch (NumberFormatException e) {
                // 파싱 에러 무시
            }
        }

        if (bookIds.isEmpty()) {
            return new CartListResponse(Collections.emptyList(), 0L, hasGuestCart);
        }

        List<GetBookResponse> bookInfos = bookFeignClient.getBooksBulk(bookIds);

        if (bookInfos == null) bookInfos = Collections.emptyList();

        Set<Long> foundBookIds = bookInfos.stream()
                .map(GetBookResponse::bookId)
                .collect(Collectors.toSet());

        List<Long> ghostItemIds = bookIds.stream()
                .filter(id -> !foundBookIds.contains(id))
                .toList();

        if (!ghostItemIds.isEmpty()) {
            log.warn("유효하지 않은 상품 발견(삭제됨): {}. 장바구니에서 제거합니다.", ghostItemIds);
            redisTemplate.opsForHash().delete(redisKey, ghostItemIds.toArray());
        }

        List<CartDetailResponse> cartDetails = new ArrayList<>();
        long totalPrice = 0L;

        for (GetBookResponse book : bookInfos) {
            if (book == null) continue;

            int quantity = quantityMap.getOrDefault(book.bookId(), 0);
            int price = (book.price() != null) ? book.price() : 0;
            int itemTotalPrice = price * quantity;

            cartDetails.add(new CartDetailResponse(book.bookId(), book.title(), price, quantity, itemTotalPrice, book.image()));
            totalPrice += itemTotalPrice;
        }

        return new CartListResponse(cartDetails, totalPrice, hasGuestCart);
    }

    private Map<Object, Object> loadFromDbAndRestoreToRedis(Long memberId, String key) {
        List<CartItem> dbItems = cartItemRepository.findByCart_Member_Id(memberId);

        if (dbItems.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> restoreData = new HashMap<>();
        for (CartItem item : dbItems) {
            restoreData.put(String.valueOf(item.getBookId()), String.valueOf(item.getQuantity()));
        }

        redisTemplate.opsForHash().putAll(key, restoreData);
        redisTemplate.expire(key, CART_TTL_DAYS, TimeUnit.DAYS);

        return new HashMap<>(restoreData);
    }

    private int parseQuantity(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            log.warn("Failed to parse quantity: {}", value);
            return 0;
        }
    }
}