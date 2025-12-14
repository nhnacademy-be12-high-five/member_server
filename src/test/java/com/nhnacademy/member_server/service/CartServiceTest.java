package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.dto.cartRequest.CartAddRequest;
import com.nhnacademy.member_server.dto.cartRequest.CartItemUpdateRequest;
import com.nhnacademy.member_server.dto.cartResponse.CartListResponse;
import com.nhnacademy.member_server.dto.cartResponse.GetBookResponse;
import com.nhnacademy.member_server.entity.cartEntity.Cart;
import com.nhnacademy.member_server.entity.cartEntity.CartItem;
import com.nhnacademy.member_server.entity.member.Member;
import com.nhnacademy.member_server.exception.BusinessException;
import com.nhnacademy.member_server.exception.ErrorCode;
import com.nhnacademy.member_server.feign.BookFeignClient;
import com.nhnacademy.member_server.repository.CartItemRepository;
import com.nhnacademy.member_server.repository.CartRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.service.impl.CartServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @InjectMocks CartServiceImpl cartService;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock BookFeignClient bookFeignClient;
    @Mock CartItemRepository cartItemRepository;
    @Mock CartRepository cartRepository;
    @Mock MemberRepository memberRepository;
    @Mock HashOperations<String, Object, Object> hashOperations;
    @Mock SetOperations<String, Object> setOperations;

    /**
     * [요청사항 반영]
     * CartItem 엔티티 테스트를 별도 파일이 아닌 Inner Class로 포함시켜 관리
     */
    @Nested
    @DisplayName("CartItem Entity Test")
    class CartItemEntityTest {
        @Test
        @DisplayName("CartItem 생성 및 수량 변경 테스트")
        void entityLogicTest() {
            Member member = new Member();
            Cart cart = new Cart(member);
            CartItem cartItem = new CartItem(100L, 2, cart);

            assertThat(cartItem.getBookId()).isEqualTo(100L);
            assertThat(cartItem.getQuantity()).isEqualTo(2);

            // updateQuantity 메서드 테스트 (서비스에서 안 쓰더라도 엔티티 커버리지를 위해 호출)
            cartItem.updateQuantity(5);
            assertThat(cartItem.getQuantity()).isEqualTo(5);
        }
    }

    // --- CartServiceImpl 테스트 시작 ---

    @Test
    @DisplayName("장바구니 담기 - 정상 (회원)")
    void addToCart_Member() {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(redisTemplate.opsForSet()).willReturn(setOperations);

        CartAddRequest request = new CartAddRequest(100L, 2);
        cartService.addToCart(request, 1L, null);

        verify(hashOperations).increment(anyString(), eq("100"), eq(2L));
        verify(setOperations).add(eq("cart:dirty"), anyString());
    }

    @Test
    @DisplayName("장바구니 담기 - Redis 예외 발생 시 REDIS_SERVER_ERROR 변환")
    void addToCart_RedisError() {
        given(redisTemplate.opsForHash()).willThrow(new RuntimeException("Redis Fail"));
        CartAddRequest request = new CartAddRequest(100L, 1);

        assertThatThrownBy(() -> cartService.addToCart(request, 1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REDIS_SERVER_ERROR);
    }

    @Test
    @DisplayName("장바구니 조회 - 전체 NULL 입력 시 빈 리스트")
    void getCartItemList_AllNull() {
        CartListResponse res = cartService.getCartItemList(null, null);
        assertThat(res.items()).isEmpty();
    }

    @Test
    @DisplayName("장바구니 조회 - Redis Hit & Feign 정상 응답")
    void getCartItemList_Normal() {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.entries(anyString())).willReturn(Map.of("100", "2"));

        // Feign Mock
        GetBookResponse book = new GetBookResponse(100L, "Title", 1000, "img");
        given(bookFeignClient.getBooksBulk(anyList())).willReturn(List.of(book));

        CartListResponse res = cartService.getCartItemList(1L, null);

        assertThat(res.items()).hasSize(1);
        assertThat(res.items().getFirst().title()).isEqualTo("Title");
        assertThat(res.totalCartPrice()).isEqualTo(2000); // 1000 * 2
    }

    @Test
    @DisplayName("장바구니 조회 - Redis Miss -> DB 복구 로직 (restoreCartOnLogin 로직 포함)")
    void getCartItemList_RestoreFromDB() {
        // 1. Redis 조회 결과 Empty
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.entries(anyString())).willReturn(Collections.emptyMap());

        // 2. DB 조회 Mock
        Member member = new Member();
        Cart cart = new Cart(member);
        CartItem item = new CartItem(100L, 5, cart);
        given(cartItemRepository.findByCart_Member_Id(1L)).willReturn(List.of(item));

        // 3. Feign Mock
        given(bookFeignClient.getBooksBulk(anyList())).willReturn(List.of(new GetBookResponse(100L, "Book", 100, "img")));

        CartListResponse res = cartService.getCartItemList(1L, null);

        assertThat(res.items()).hasSize(1);
        // DB -> Redis 복구 확인 (putAll 호출)
        verify(hashOperations).putAll(anyString(), anyMap());
    }

    @Test
    @DisplayName("장바구니 조회 - Redis/DB 모두 없음")
    void getCartItemList_EmptyAll() {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.entries(anyString())).willReturn(Collections.emptyMap());
        given(cartItemRepository.findByCart_Member_Id(1L)).willReturn(Collections.emptyList());

        CartListResponse res = cartService.getCartItemList(1L, null);
        assertThat(res.items()).isEmpty();
    }

    @Test
    @DisplayName("장바구니 조회 - Feign 응답 NULL 또는 일부 NULL 포함")
    void getCartItemList_FeignIssues() {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.entries(anyString())).willReturn(Map.of("100", "1"));

        // Case 1: 아예 null 리턴
        given(bookFeignClient.getBooksBulk(anyList())).willReturn(null);
        CartListResponse res1 = cartService.getCartItemList(1L, null);
        assertThat(res1.items()).isEmpty();

        // Case 2: 리스트 안에 null 포함
        List<GetBookResponse> listWithNull = new ArrayList<>();
        listWithNull.add(null);
        given(bookFeignClient.getBooksBulk(anyList())).willReturn(listWithNull);
        CartListResponse res2 = cartService.getCartItemList(1L, null);
        assertThat(res2.items()).isEmpty();
    }

    @Test
    @DisplayName("장바구니 조회 - 알 수 없는 에러 시 Redis 에러로 래핑")
    void getCartItemList_UnknownError() {
        given(redisTemplate.opsForHash()).willThrow(new RuntimeException("Boom"));

        assertThatThrownBy(() -> cartService.getCartItemList(1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REDIS_SERVER_ERROR);
    }

    @Test
    @DisplayName("장바구니 조회 - BusinessException 발생 시 그대로 던짐 (래핑 X)")
    void getCartItemList_RethrowBusinessException() {
        // 일부러 calculateCartResponse 내부에서 에러가 나도록 유도하거나,
        // 혹은 직접 Mocking을 통해 BusinessException을 발생시킴.
        // 여기서는 calculateCartResponse 내부 Feign 예외를 가정하기 어려우므로
        // redisTemplate이 BusinessException을 던진다고 가정 (이론상 가능)
        given(redisTemplate.opsForHash()).willThrow(new BusinessException(ErrorCode.BOOK_SERVICE_ERROR));

        assertThatThrownBy(() -> cartService.getCartItemList(1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOK_SERVICE_ERROR);
    }

    @Test
    @DisplayName("수량 변경 - 성공")
    void updateQuantity_Success() {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.hasKey(anyString(), anyString())).willReturn(true);
        given(redisTemplate.opsForSet()).willReturn(setOperations);

        cartService.updateCartItemQuantity(1L, null, new CartItemUpdateRequest(100L, 5));

        verify(hashOperations).put(anyString(), eq("100"), eq("5"));
    }

    @Test
    @DisplayName("수량 변경 - 상품 없음 (CART_ITEM_NOT_FOUND)")
    void updateQuantity_NotFound() {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.hasKey(anyString(), anyString())).willReturn(false);

        assertThatThrownBy(() -> cartService.updateCartItemQuantity(1L, null, new CartItemUpdateRequest(100L, 5)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("수량 변경 - Redis 에러")
    void updateQuantity_RedisError() {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.hasKey(anyString(), anyString())).willThrow(new RuntimeException("Redis"));

        assertThatThrownBy(() -> cartService.updateCartItemQuantity(1L, null, new CartItemUpdateRequest(100L, 5)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REDIS_SERVER_ERROR);
    }

    @Test
    @DisplayName("단건 삭제 - 성공")
    void deleteCartItem() {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(redisTemplate.opsForSet()).willReturn(setOperations);

        cartService.deleteCartItem(1L, null, 100L);

        verify(hashOperations).delete(anyString(), anyString());
    }

    @Test
    @DisplayName("전체 삭제 - 이미 비어있음")
    void deleteAll_Empty() {
        given(redisTemplate.delete(anyString())).willReturn(false); // 삭제된 게 없음
        given(redisTemplate.opsForSet()).willReturn(setOperations);

        cartService.deleteAllCartItem(1L, null);

        // 그래도 로직은 끝까지 수행됨
        verify(setOperations).add(eq("cart:dirty"), anyString());
    }

    @Test
    @DisplayName("전체 삭제 - Redis 에러")
    void deleteAll_Error() {
        given(redisTemplate.delete(anyString())).willThrow(new RuntimeException("Fail"));

        assertThatThrownBy(() -> cartService.deleteAllCartItem(1L, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("게스트 카트 병합 - 성공")
    void migrateGuestCart_Success() {
        given(redisTemplate.hasKey(anyString())).willReturn(true);
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(redisTemplate.opsForSet()).willReturn(setOperations);
        given(hashOperations.entries(anyString())).willReturn(Map.of("100", "2"));

        cartService.migrateGuestCart("guest", 1L);

        verify(hashOperations).increment(anyString(), eq("100"), eq(2L));
        verify(redisTemplate).delete(contains("cart:g:"));
    }

    @Test
    @DisplayName("게스트 카트 병합 - Redis 에러 발생 시 RuntimeException")
    void migrateGuestCart_Error() {
        given(redisTemplate.hasKey(anyString())).willThrow(new RuntimeException("Oops"));

        assertThatThrownBy(() -> cartService.migrateGuestCart("guest", 1L))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("로그인 시 복구 - 데이터 없으면 스킵")
    void restoreCartOnLogin_NoData() {
        // DB 데이터 없음
        given(cartItemRepository.findByCart_Member_Id(1L)).willReturn(Collections.emptyList());

        cartService.restoreCartOnLogin(1L);

        // Redis 로직 실행 안됨 -> 불필요한 스터빙 에러 방지됨
        verify(redisTemplate, never()).opsForHash();
    }

    @Test
    @DisplayName("DB 동기화 (syncToDb) - 회원 없음")
    void syncToDb_MemberNotFound() {
        given(memberRepository.findById(99L)).willReturn(Optional.empty());

        cartService.syncToDb(99L, Map.of("100", "1"));

        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("DB 동기화 (syncToDb) - 파싱 에러 (무시하고 진행)")
    void syncToDb_ParsingError() {
        Member member = new Member();
        Cart cart = new Cart(member);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(cartRepository.findByMember_Id(1L)).willReturn(Optional.of(cart));

        // 잘못된 데이터 섞임
        Map<Object, Object> redisMap = new HashMap<>();
        redisMap.put("100", "2");
        redisMap.put("bad", "data");

        cartService.syncToDb(1L, redisMap);

        // 정상 데이터인 1개만 저장되어야 함 (혹은 리스트 필터링 후 저장)
        // verify로 saveAll 호출 여부 확인
        verify(cartItemRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("비회원 카트만 삭제")
    void deleteGuestCartOnly() {
        cartService.deleteGuestCartOnly("guest");
        verify(redisTemplate).delete("cart:g:guest");
    }

    @Test
    @DisplayName("비회원 카트만 삭제 - 에러")
    void deleteGuestCartOnly_Error() {
        given(redisTemplate.delete(anyString())).willThrow(new RuntimeException("Fail"));

        assertThatThrownBy(() -> cartService.deleteGuestCartOnly("guest"))
                .isInstanceOf(BusinessException.class);
    }
}