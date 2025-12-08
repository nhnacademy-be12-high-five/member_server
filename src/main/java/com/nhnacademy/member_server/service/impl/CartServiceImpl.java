package com.nhnacademy.member_server.service.impl;

import com.nhnacademy.member_server.dto.cartRequest.CartAddRequest;
import com.nhnacademy.member_server.dto.cartRequest.CartItemUpdateRequest;
import com.nhnacademy.member_server.dto.cartResponse.*;
import com.nhnacademy.member_server.entity.Member;
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

//    // 장바구니 리스트 조회
//    @Override
//    @Transactional(readOnly = true)
//    public CartListResponse getCartItemList(Long memberId, String guestId) {
//        boolean hasGuestCart = false;
//        // 방어 로직
//        if (memberId == null && guestId == null) {
//            return new CartListResponse(Collections.emptyList(), 0L, hasGuestCart);
//        }
//
//        String key = getRedisKey(memberId, guestId);
//        try {
//            Map<Object, Object> redisItems = redisTemplate.opsForHash().entries(key);
//
//            // 혹시 모르니 한번 더 체크
//            if (redisItems.isEmpty() && memberId != null) {
//                redisItems = loadFromDbAndRestoreToRedis(memberId, key);
//            }
//
//            if (memberId != null && guestId != null) {
//                String guestKey = "cart:g:" + guestId;
//                // Redis에 키가 존재하고, 내용물이 비어있지 않은지 체크
//                hasGuestCart = redisTemplate.hasKey(guestKey);
//            }
//
//            if (redisItems.isEmpty()) {
//                return new CartListResponse(Collections.emptyList(), 0L, hasGuestCart);
//            }
//
//            redisTemplate.expire(key, 7, TimeUnit.DAYS);
//
//            try {
//                return calculateCartResponse(redisItems, hasGuestCart);
//            } catch (Exception e) {
//                // e.getMessage()가 null일 수 있으니 e 자체를 로깅
//                log.error("Book Service 연동 또는 데이터 계산 실패", e);
//                throw new BusinessException(ErrorCode.BOOK_SERVICE_ERROR); // 에러 코드를 분리하는 것을 추천
//            }
//
//        } catch (Exception e) {
//            log.error("장바구니 조회 중 redis error: ", e);
//
//            /// 여기에 레디스 쪽이 고장났을 경우 임의로 DB 에서 꺼내오는 로직을 작성 할 수 있음
//            throw new BusinessException(ErrorCode.REDIS_SERVER_ERROR);
//        }
//    }

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
    @Transactional
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
                Object value = entry.getValue();

                int quantity;
                try {
                    quantity = Integer.parseInt(String.valueOf(value));
                } catch (NumberFormatException e) {
                    quantity = 0;
                }

                // 여기서 redis의 장점이 나옴 없으면 생성 있으면 증가
                redisTemplate.opsForHash().increment(memberKey, bookId, quantity);
            }

            redisTemplate.delete(guestKey);

            redisTemplate.opsForSet().add(dirtyKey, String.valueOf(memberId));

            redisTemplate.expire(memberKey, 7, TimeUnit.DAYS);
        }
//        catch (Exception e) {
//            log.error("장바구니 병합 중 redis 오류 발생: {}", e.getMessage());
//            throw new BusinessException(ErrorCode.REDIS_SERVER_ERROR);
//        }
            catch (Exception e) {
                log.error("진짜 에러 원인 확인용 로그", e);
                throw new RuntimeException(e); // 있는 그대로 터뜨리기
            }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW) // REQUIRES_NEW -> 각 회원을 독립적으로 지키기 위해서
    public void syncToDb(Long memberId, Map<Object, Object> redisItems) {

        Member member = memberRepository.findById(memberId).orElse(null);

        if (member == null) {
            log.error("❌ DB 동기화 실패: 존재하지 않는 회원입니다. (MemberId: {})", memberId);
            return;
        }

        Cart cart = cartRepository.findByMember_Id(memberId)
                .orElseGet(() -> cartRepository.save(new Cart(member)));

        // DELETE
        cartItemRepository.deleteAllByCartId(cart.getId());

        cartItemRepository.flush();

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
//    private CartListResponse calculateCartResponse(Map<Object, Object> redisItems, boolean hasGuestCart) {
//        List<Long> bookIds = new ArrayList<>();
//        Map<Long, Integer> quantityMap = new HashMap<>();
//
//        // 1. Redis 데이터 파싱 (Integer 캐스팅 안전하게)
//        for (Map.Entry<Object, Object> entry : redisItems.entrySet()) {
//            try {
//                Long bookId = Long.valueOf(String.valueOf(entry.getKey()));
//
//                Object value = entry.getValue();
//                int quantity = 0;
//                if (value instanceof Integer) {
//                    quantity = (Integer) value;
//                } else if (value != null) {
//                    quantity = Integer.parseInt(String.valueOf(value));
//                }
//
//                bookIds.add(bookId);
//                quantityMap.put(bookId, quantity);
//            } catch (NumberFormatException e) {
//                log.warn("Redis 데이터 파싱 중 잘못된 형식 발견: key={}, value={}", entry.getKey(), entry.getValue());
//                // 잘못된 데이터는 무시하고 계속 진행
//            }
//        }
//
//        if (bookIds.isEmpty()) {
//            return new CartListResponse(Collections.emptyList(), 0L, hasGuestCart);
//        }
//
//        List<GetBookResponse> bookInfos = bookFeignClient.getBooksBulk(bookIds);
//        if (bookInfos == null) {
//            log.warn("Book Service에서 null 응답을 받았습니다. bookIds={}", bookIds);
//            bookInfos = Collections.emptyList(); // 빈 리스트로 대체하여 에러 방지
//        }
//
//        List<CartDetailResponse> cartDetails = new ArrayList<>();
//        long totalPrice = 0L;
//
//        // 3. 데이터 조립 (용의자 1 검거)
//        for (GetBookResponse book : bookInfos) {
//            if (book == null) continue; // 리스트 안에 null이 있을 경우 대비
//
//            int quantity = quantityMap.getOrDefault(book.bookId(), 0);
//
//            // [핵심] 가격이 null이면 0원으로 처리하여 NPE 방지
//            int price = (book.price() != null) ? book.price() : 0;
//            int itemTotalPrice = price * quantity;
//
//            cartDetails.add(new CartDetailResponse(
//                    book.bookId(),
//                    book.title(),
//                    price,          // 안전한 price 사용
//                    quantity,
//                    itemTotalPrice,
//                    book.image()
//            ));
//            totalPrice += itemTotalPrice;
//        }
//
//        return new CartListResponse(cartDetails, totalPrice, hasGuestCart);
//    }
    // [디버깅용] 장바구니 리스트 조회
    @Override
    @Transactional(readOnly = true)
    public CartListResponse getCartItemList(Long memberId, String guestId) {
        log.info("============== [장바구니 조회 시작] ==============");
        log.info("1. 요청 파라미터 확인 -> memberId: {}, guestId: {}", memberId, guestId);

        boolean hasGuestCart = false;
        if (memberId == null && guestId == null) {
            log.warn("❌ 회원ID와 비회원ID가 둘 다 NULL입니다. 빈 리스트 반환.");
            return new CartListResponse(Collections.emptyList(), 0L, hasGuestCart);
        }

        String key = getRedisKey(memberId, guestId);
        log.info("2. 생성된 Redis Key: {}", key);

        try {
            // Redis 데이터 조회
            Map<Object, Object> redisItems = redisTemplate.opsForHash().entries(key);
            log.info("3. Redis 조회 결과(Map) 크기: {}", redisItems.size());

            // 내용물 살짝 훔쳐보기
            redisItems.forEach((k, v) -> log.info("   -> Redis 아이템: Key(BookId)={}, Value(Qty)={}", k, v));

            // DB 백업 조회 로직 (기존 유지)
            if (redisItems.isEmpty() && memberId != null) {
                log.info("Redis가 비어있어 DB에서 복구 시도...");
                redisItems = loadFromDbAndRestoreToRedis(memberId, key);
                log.info("복구 후 Redis 크기: {}", redisItems.size());
            }

            if (memberId != null && guestId != null) {
                String guestKey = "cart:g:" + guestId;
                hasGuestCart = redisTemplate.hasKey(guestKey);
                log.info("비회원 장바구니 존재 여부 확인(guestKey={}): {}", guestKey, hasGuestCart);
            }

            if (redisItems.isEmpty()) {
                log.info("❌ 최종적으로 장바구니가 비어있습니다. 빈 리스트 반환.");
                return new CartListResponse(Collections.emptyList(), 0L, hasGuestCart);
            }

            redisTemplate.expire(key, 7, TimeUnit.DAYS);

            try {
                // 계산 로직 호출
                return calculateCartResponse(redisItems, hasGuestCart);
            } catch (Exception e) {
                log.error("❌ Book Service 연동 또는 데이터 계산 실패", e);
                throw new BusinessException(ErrorCode.BOOK_SERVICE_ERROR);
            }

        } catch (Exception e) {
            log.error("❌ 장바구니 조회 중 치명적 에러: ", e);
            throw new BusinessException(ErrorCode.REDIS_SERVER_ERROR);
        } finally {
            log.info("============== [장바구니 조회 종료] ==============");
        }
    }

    // [디버깅용] Feign Client 연동 및 계산
    private CartListResponse calculateCartResponse(Map<Object, Object> redisItems, boolean hasGuestCart) {
        log.info("--- [데이터 계산 및 Feign 요청 시작] ---");

        List<Long> bookIds = new ArrayList<>();
        Map<Long, Integer> quantityMap = new HashMap<>();

        for (Map.Entry<Object, Object> entry : redisItems.entrySet()) {
            try {
                Long bookId = Long.valueOf(String.valueOf(entry.getKey()));
                Object value = entry.getValue();
                int quantity = Integer.parseInt(String.valueOf(value));

                bookIds.add(bookId);
                quantityMap.put(bookId, quantity);
            } catch (NumberFormatException e) {
                log.warn("데이터 파싱 에러(무시됨): key={}, value={}", entry.getKey(), entry.getValue());
            }
        }

        log.info("4. 추출된 Book IDs: {}", bookIds);

        if (bookIds.isEmpty()) {
            log.warn("Book ID가 하나도 추출되지 않았습니다.");
            return new CartListResponse(Collections.emptyList(), 0L, hasGuestCart);
        }

        // Feign 요청
        log.info("5. Book Service로 상세 정보 요청 중...");
        List<GetBookResponse> bookInfos = bookFeignClient.getBooksBulk(bookIds);

        if (bookInfos == null) {
            log.error("❌ Book Service 응답이 NULL 입니다!");
            bookInfos = Collections.emptyList();
        } else {
            log.info("✅ Book Service 응답 도착! 가져온 책 개수: {}", bookInfos.size());
            bookInfos.forEach(b -> log.info("   -> 책 정보: ID={}, 제목={}, 가격={}", b.bookId(), b.title(), b.price()));
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

        log.info("6. 최종 조립된 리스트 개수: {}, 총 금액: {}", cartDetails.size(), totalPrice);
        return new CartListResponse(cartDetails, totalPrice, hasGuestCart);
    }

    // --- redis 복구 메소드 ---
    private Map<Object, Object> loadFromDbAndRestoreToRedis(Long memberId, String key) {
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