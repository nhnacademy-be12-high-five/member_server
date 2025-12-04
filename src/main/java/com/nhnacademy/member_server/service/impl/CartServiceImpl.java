package com.nhnacademy.member_server.service.impl;

import com.nhnacademy.member_server.dto.cartRequest.CartAddRequest;
import com.nhnacademy.member_server.dto.cartRequest.CartItemUpdateRequest;
import com.nhnacademy.member_server.dto.cartResponse.CartAddResponse;
import com.nhnacademy.member_server.dto.cartResponse.CartDetailResponse;
import com.nhnacademy.member_server.dto.cartResponse.CartListResponse;
import com.nhnacademy.member_server.dto.cartResponse.CartUpdateResponse;
import com.nhnacademy.member_server.entity.cartEntity.Cart;
import com.nhnacademy.member_server.entity.cartEntity.CartItem;
import com.nhnacademy.member_server.exception.BusinessException;
import com.nhnacademy.member_server.exception.ErrorCode;
import com.nhnacademy.member_server.feign.BookFeignClient;
import com.nhnacademy.member_server.repository.CartItemRepository;
import com.nhnacademy.member_server.repository.CartRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.service.CartService;
import feign.FeignException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    @Transactional(readOnly = true)
    public CartListResponse getCartItemList(Long memberId, String guestId) {
        boolean hasGuestCart = false;
        // 방어 로직
        if (memberId == null && guestId == null) {
            return new CartListResponse(Collections.emptyList(), 0L, hasGuestCart);
        }

        String key = getRedisKey(memberId, guestId);
        try {
            Map<Object, Object> redisItems = redisTemplate.opsForHash().entries(key);

            // 혹시 모르니 한번 더 체크
            if (redisItems.isEmpty() && memberId != null) {
                redisItems = loadFromDbAndRestoreToRedis(memberId, key);
            }

            if (memberId != null && guestId != null) {
                String guestKey = "cart:g:" + guestId;
                // Redis에 키가 존재하고, 내용물이 비어있지 않은지 체크
                hasGuestCart = redisTemplate.hasKey(guestKey);
            }

            if (redisItems.isEmpty()) {
                return new CartListResponse(Collections.emptyList(), 0L, hasGuestCart);
            }

            redisTemplate.expire(key, 7, TimeUnit.DAYS);

            return calculateCartResponse(redisItems, hasGuestCart);
        } catch (Exception e) {
            log.error("장바구니 조회 중 redis error: {}", e.getMessage());

            /// 여기에 레디스 쪽이 고장났을 경우 임의로 DB 에서 꺼내오는 로직을 작성 할 수 있음
            throw new BusinessException(ErrorCode.REDIS_SERVER_ERROR);
        }
    }

    // 로그인 시 비동기 복구 -> 로그인 직후 실행됨
    @Async
    @Transactional
    @Override
    public void restoreCartOnLogin(Long memberId) {
        String key = getRedisKey(memberId, null);
        loadFromDbAndRestoreToRedis(memberId, key);
    }

    // 장바구니 담기
    @Override
    public CartAddResponse addToCart(CartAddRequest request, Long memberId, String guestId) {
        String key = getRedisKey(memberId, guestId);
        String bookIdStr = String.valueOf(request.bookId());

        try {
            // 해당하는 책이 있으면 request.quantity 만큼 증가, 없으면 알아서 만들어서 증가
            redisTemplate.opsForHash().increment(key, bookIdStr, request.quantity());

            // 7일 뒤에 사라짐 (그래도 주말 구매할 사람들까진 생각해줘야지)
            redisTemplate.expire(key, 7, TimeUnit.DAYS);

            // db를 업데이트해야하는 멤버 아이디를 알려줌
            if (memberId != null) {
                redisTemplate.opsForSet().add(DIRTY_KEY, String.valueOf(memberId));
            }
        } catch (Exception e) {
            log.error("장바구니 담기 redis error: {}", e.getMessage());
            throw new BusinessException(ErrorCode.REDIS_SERVER_ERROR);
        }
        return new CartAddResponse(key, request.bookId(), request.quantity());
    }

    // 수량 변경
    @Override
    public CartUpdateResponse updateCartItemQuantity(Long memberId, String guestId, CartItemUpdateRequest request) {
        String key = getRedisKey(memberId, guestId);
        String bookIdStr = String.valueOf(request.bookId());

        try {
            if (!redisTemplate.opsForHash().hasKey(key, bookIdStr)) {
                throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
            }

            // 덮어쓰기
            redisTemplate.opsForHash().put(key, bookIdStr, String.valueOf(request.quantity()));

            // redis 7일 연장
            redisTemplate.expire(key, 7, TimeUnit.DAYS);

            if (memberId != null) {
                redisTemplate.opsForSet().add(DIRTY_KEY, String.valueOf(memberId));
            }
        } catch (Exception e) {
            log.error("장바구니 수량 변경 redis error: {}", e.getMessage());
            throw new BusinessException(ErrorCode.REDIS_SERVER_ERROR);
        }

        return new CartUpdateResponse(key, request.bookId(), request.quantity());
    }

    // 책 자체를 삭제
    @Override
    public void deleteCartItem(Long memberId, String guestId, Long bookId) {
        String key = getRedisKey(memberId, guestId);
        try {
            redisTemplate.opsForHash().delete(key, String.valueOf(bookId));

            if (memberId != null) {
                redisTemplate.opsForSet().add(DIRTY_KEY, String.valueOf(memberId));
            }
        } catch (Exception e) {
            log.error("장바구니 단건 삭제 redis error: {}", e.getMessage());
            throw new BusinessException(ErrorCode.REDIS_SERVER_ERROR);
        }
    }

    // 장바구니 비우기
    @Override
    public void deleteAllCartItem(Long memberId, String guestId) {
        String key = getRedisKey(memberId, guestId);

        try {
            Boolean isDeleted = redisTemplate.delete(key);

            if (Boolean.TRUE.equals(isDeleted)) {
                log.info("장바구니 삭제 성공. Key: {}", key);
            } else {
                log.info("이미 비어있는 장바구니 삭제 요청됨. Key: {}", key);
            }

            if (memberId != null) {
                Long addedCount = redisTemplate.opsForSet().add(DIRTY_KEY, String.valueOf(memberId));

                if (addedCount == null || addedCount == 0) {
                    log.debug("이미 동기화 대기열(Dirty Set)에 존재하는 회원입니다. MemberId: {}", memberId);
                } else {
                    log.info("DB 동기화 대기열 등록 완료. MemberId: {}", memberId);
                }
            }

        } catch (Exception e) {
            log.error("장바구니 삭제 중 Redis 오류 발생! MemberId: {}, GuestId: {}", memberId, guestId, e);

            throw new BusinessException(ErrorCode.REDIS_SERVER_ERROR);
        }
    }

    @Override
    public void migrateGuestCart(String guestId, Long memberId) {
        String guestKey = "cart:g:" + guestId;
        String memberKey = "cart:m:" + memberId;
        String dirtyKey = "cart:dirty";
        try {
            if (!redisTemplate.hasKey(guestKey)) {
                return;
            }

            Map<Object, Object> guestItems = redisTemplate.opsForHash().entries(guestKey);

            if (!redisTemplate.hasKey(memberKey)) {
                loadFromDbAndRestoreToRedis(memberId, memberKey);
            }

            // 합치기 수량
            for (Map.Entry<Object, Object> entry : guestItems.entrySet()) {
                String bookId = (String) entry.getKey();
                int quantity = Integer.parseInt((String) entry.getValue());

                // 여기서 redis의 장점이 나옴 없으면 생성 있으면 증가
                redisTemplate.opsForHash().increment(memberKey, bookId, quantity);
            }

            redisTemplate.delete(guestKey);

            redisTemplate.opsForSet().add(dirtyKey, String.valueOf(memberId));

            redisTemplate.expire(memberKey, 7, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("장바구니 병합 중 redis 오류 발생: {}", e.getMessage());
            throw new BusinessException(ErrorCode.REDIS_SERVER_ERROR);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW) // REQUIRES_NEW -> 각 회원을 독립적으로 지키기 위해서
    public void syncToDb(Long memberId, Map<Object, Object> redisItems) {
        // 회원 장바구니에 저장하기 위해 없으면 만들어줌
        Cart cart = cartRepository.findByMember_Id(memberId)
                .orElseGet(() -> cartRepository.save(new Cart(memberRepository.getReferenceById(memberId))));

        // DELETE
        cartItemRepository.deleteAllByCartId(cart.getId());

        // Redis 데이터 DB로 변환
        if (redisItems != null && !redisItems.isEmpty()) {
            List<CartItem> items = redisItems.entrySet().stream()
                    .map(entry -> {
                        try {
                            return new CartItem(
                                    Long.parseLong((String) entry.getKey()),
                                    Integer.parseInt((String) entry.getValue()),
                                    cart);
                        } catch (NumberFormatException e) {
                            log.error("DB 동기화 중 파싱 에러 MemberId: {}, item: {}", memberId, entry);
                            return null;
                        }
                    })
                    .filter(item -> item != null)
                    .toList();
            // INSERT
            cartItemRepository.saveAll(items);
        }
    }

    // 비회원 redis 삭제 -> 장바구니 합칠 때 사용하는 메소드
    @Override
    public void deleteGuestCartOnly(String guestId) {
        String key = "cart:g:" + guestId;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("비회원 장바구니 삭제 중 Redis 오류 발생! GuestId: {}", guestId, e);
            throw new BusinessException(ErrorCode.REDIS_SERVER_ERROR);
        }
    }

        ///  헬퍼 메서드

        // feignClient로 책 정보 조회 및 DTO 변환 메서드
        private CartListResponse calculateCartResponse (Map < Object, Object > redisItems,boolean hasGuestCart){
            // 책 ID 리스트 추출
            List<Long> bookIds = new ArrayList<>();
            Map<Long, Integer> quantityMap = new HashMap<>();

            for (Map.Entry<Object, Object> entry : redisItems.entrySet()) {
                try {
                    Long bId = Long.valueOf((String) entry.getKey());
                    Integer qty = Integer.parseInt((String) entry.getValue());
                    bookIds.add(bId);
                    quantityMap.put(bId, qty);
                } catch (NumberFormatException e) {
                    log.warn("Redis 장바구니 데이터 파싱 에러 (무시됨) - Key: {}, Value: {}", entry.getKey(), entry.getValue());
                    // 잘못된 데이터는 건너뛰고 계속 진행
                }
            }

            if (bookIds.isEmpty()) {
                return new CartListResponse(Collections.emptyList(), 0L, hasGuestCart);
            }

            // Feign과 bookId로 책 정보 Bulk 조회
            List<CartDetailResponse> bookInfoList;
            try {
                bookInfoList = bookFeignClient.getBooksBulk(bookIds);
            } catch (FeignException e) {
                log.error("Book Service 연동 실패: {}", e.getMessage());
                throw new BusinessException(ErrorCode.BOOK_SERVICE_UNAVAILABLE);
            }

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
            return new CartListResponse(responseList, totalCartPrice, hasGuestCart);
        }

        // --- redis 복구 메소드 ---
        private Map<Object, Object> loadFromDbAndRestoreToRedis (Long memberId, String key){
            // 1. DB에서 회원의 장바구니 아이템 조회 (Fetch Join 등으로 성능 최적화 추천)
            // CartRepository -> CartItemRepository를 통해 조회
            List<CartItem> dbItems = cartItemRepository.findByCart_Member_Id(memberId);

            if (dbItems.isEmpty()) {
                return Collections.emptyMap();
            }

            // 2. DB 데이터를 Redis 포맷(Map)으로 변환
            Map<String, String> restoreData = new HashMap<>();
            for (CartItem item : dbItems) {
                restoreData.put(String.valueOf(item.getBookId()), String.valueOf(item.getQuantity()));
            }

            // 3. Redis에 '몰아넣기' (Restore)
            redisTemplate.opsForHash().putAll(key, restoreData);

            // 4. Redis 수명 설정 (회원이니 넉넉하게 다시 시작)
            redisTemplate.expire(key, 7, TimeUnit.DAYS);

            log.info("장바구니에서 db 데이터 redis로 변환: {}", memberId);

            return new HashMap<>(restoreData);
        }
    }