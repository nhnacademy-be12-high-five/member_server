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

            cartItem.updateQuantity(5);
            assertThat(cartItem.getQuantity()).isEqualTo(5);
        }
    }


    // =================================================================
    // 1. 장바구니 담기 (addToCart)
    // =================================================================

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
    @DisplayName("장바구니 담기 - Redis 예외 발생")
    void addToCart_RedisError() {
        given(redisTemplate.opsForHash()).willThrow(new RuntimeException("Redis Fail"));
        CartAddRequest request = new CartAddRequest(100L, 1);

        assertThatThrownBy(() -> cartService.addToCart(request, 1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REDIS_SERVER_ERROR);
    }

    @Test
    @DisplayName("장바구니 담기 - 수량 제한 초과 (100개)")
    void addToCart_QuantityExceedsMax() {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);

        given(hashOperations.get(anyString(), anyString())).willReturn("90");

        CartAddRequest request = new CartAddRequest(100L, 11);

        assertThatThrownBy(() -> cartService.addToCart(request, 1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    // =================================================================
    // 2. 장바구니 조회 (getCartItemList) - 복구, Ghost Item, 파싱 에러 등
    // =================================================================

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

        GetBookResponse book = new GetBookResponse(100L, "Title", 1000, "img");
        given(bookFeignClient.getBooksBulk(anyList())).willReturn(List.of(book));

        CartListResponse res = cartService.getCartItemList(1L, null);

        assertThat(res.items()).hasSize(1);
        assertThat(res.items().getFirst().totalPrice()).isEqualTo(2000);
    }

    @Test
    @DisplayName("장바구니 조회 - Redis Miss -> DB 복구 로직 (Restore)")
    void getCartItemList_RestoreFromDB() {
        // 1. Redis Empty
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.entries(anyString())).willReturn(Collections.emptyMap());

        // 2. DB Data Exists
        CartItem item = new CartItem(100L, 5, mock(Cart.class));
        given(cartItemRepository.findByCart_Member_Id(1L)).willReturn(List.of(item));

        // 3. Feign Mock
        given(bookFeignClient.getBooksBulk(anyList())).willReturn(List.of(new GetBookResponse(100L, "Book", 100, "img")));

        CartListResponse res = cartService.getCartItemList(1L, null);

        assertThat(res.items()).hasSize(1);
        verify(hashOperations).putAll(anyString(), anyMap()); // Restore 확인
    }

    @Test
    @DisplayName("장바구니 조회 - Ghost Item(삭제된 상품) 자동 정리")
    void getCartItemList_GhostItemRemoval() {
        String redisKey = "cart:m:1";
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        // Redis: 100, 200
        given(hashOperations.entries(redisKey)).willReturn(Map.of("100", "2", "200", "3"));

        // Feign: 100만 리턴 (200은 삭제됨)
        GetBookResponse book100 = new GetBookResponse(100L, "Title", 1000, "img");
        given(bookFeignClient.getBooksBulk(anyList())).willReturn(List.of(book100));

        cartService.getCartItemList(1L, null);

        // Redis에서 200번 삭제 호출 검증 (Long 타입)
        verify(hashOperations).delete(eq(redisKey), eq(200L));
    }

    @Test
    @DisplayName("장바구니 조회 - Redis 데이터 파싱 에러 (NumberFormatException) 무시")
    void getCartItemList_ParsingError() {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        // "bad": key가 숫자가 아님, "100":"nan": value가 숫자가 아님
        Map<Object, Object> redisMap = new HashMap<>();
        redisMap.put("bad_key", "1");
        redisMap.put("100", "not_a_number");
        redisMap.put("200", "5"); // 정상

        given(hashOperations.entries(anyString())).willReturn(redisMap);
        given(bookFeignClient.getBooksBulk(anyList())).willReturn(List.of(new GetBookResponse(200L, "Book", 100, "img")));

        CartListResponse res = cartService.getCartItemList(1L, null);

        // 정상 데이터 1개만 처리됨
        assertThat(res.items()).hasSize(1);
        assertThat(res.items().getFirst().bookId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("장바구니 조회 - Feign 응답이 NULL이거나 비어있을 때")
    void getCartItemList_FeignIssues() {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.entries(anyString())).willReturn(Map.of("100", "1"));

        // NULL 리턴
        given(bookFeignClient.getBooksBulk(anyList())).willReturn(null);

        CartListResponse res = cartService.getCartItemList(1L, null);
        assertThat(res.items()).isEmpty();
    }

    // =================================================================
    // 3. 수량 변경 (Update) & 삭제 (Delete)
    // =================================================================

    @Test
    @DisplayName("수량 변경 - 성공")
    void updateQuantity_Success() {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(redisTemplate.opsForHash().hasKey(anyString(), anyString())).willReturn(true);
        given(redisTemplate.opsForSet()).willReturn(setOperations);

        cartService.updateCartItemQuantity(1L, null, new CartItemUpdateRequest(100L, 5));

        verify(hashOperations).put(anyString(), eq("100"), eq("5"));
    }

    @Test
    @DisplayName("수량 변경 - 상품 없음 (예외)")
    void updateQuantity_NotFound() {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(redisTemplate.opsForHash().hasKey(anyString(), anyString())).willReturn(false);

        assertThatThrownBy(() -> cartService.updateCartItemQuantity(1L, null, new CartItemUpdateRequest(100L, 5)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
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
    @DisplayName("전체 삭제 - 성공")
    void deleteAllCartItem() {
        given(redisTemplate.opsForSet()).willReturn(setOperations);

        cartService.deleteAllCartItem(1L, null);

        verify(redisTemplate).delete(anyString());
        verify(setOperations).add(eq("cart:dirty"), anyString());
    }

    // =================================================================
    // 4. 게스트 장바구니 병합 (Migrate) & 삭제
    // =================================================================

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
    @DisplayName("게스트 카트 병합 - 파싱 에러 (수량 0 이하이므로 스킵)")
    void migrateGuestCart_ParsingError() {
        given(redisTemplate.hasKey(anyString())).willReturn(true);
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(redisTemplate.opsForSet()).willReturn(setOperations);

        given(hashOperations.entries(anyString())).willReturn(Map.of("100", "bad_qty"));

        cartService.migrateGuestCart("guest", 1L);

        verify(hashOperations, never()).increment(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("비회원 카트만 삭제")
    void deleteGuestCartOnly() {
        cartService.deleteGuestCartOnly("guest");
        verify(redisTemplate).delete("cart:g:guest");
    }

    // =================================================================
    // 5. DB 동기화 (syncToDb) - Dirty Checking
    // =================================================================

    @Test
    @DisplayName("DB 동기화 - INSERT (DB X, Redis O)")
    void syncToDb_Insert() {
        Long memberId = 1L;
        Member member = mock(Member.class);
        Cart cart = mock(Cart.class);

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(cartRepository.findByMember_Id(memberId)).thenReturn(Optional.of(cart));
        given(cartItemRepository.findByCart_Member_Id(memberId)).willReturn(new ArrayList<>());

        Map<Object, Object> redisMap = Map.of("100", "5");

        cartService.syncToDb(memberId, redisMap);

        verify(cartItemRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("DB 동기화 - UPDATE (수량 변경)")
    void syncToDb_Update() {
        Long memberId = 1L;
        Member member = mock(Member.class);
        Cart cart = mock(Cart.class);

        lenient().when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        lenient().when(cartRepository.findByMember_Id(memberId)).thenReturn(Optional.of(cart));

        CartItem dbItem = spy(new CartItem(100L, 2, cart));
        given(cartItemRepository.findByCart_Member_Id(memberId)).willReturn(List.of(dbItem));

        Map<Object, Object> redisMap = Map.of("100", "5"); // 수량 5로 변경

        cartService.syncToDb(memberId, redisMap);

        verify(dbItem).updateQuantity(5);
        verify(cartItemRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("DB 동기화 - DELETE (Redis에서 삭제된 경우)")
    void syncToDb_Delete() {
        Long memberId = 1L;
        Member member = mock(Member.class);
        Cart cart = mock(Cart.class);

        lenient().when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        lenient().when(cartRepository.findByMember_Id(memberId)).thenReturn(Optional.of(cart));

        CartItem dbItem = new CartItem(100L, 2, cart);
        given(cartItemRepository.findByCart_Member_Id(memberId)).willReturn(List.of(dbItem));

        Map<Object, Object> redisMap = Collections.emptyMap(); // Redis 비어있음

        cartService.syncToDb(memberId, redisMap);

        verify(cartItemRepository).deleteAllInBatch(anyList());
        verify(cartItemRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("DB 동기화 - Repository 예외 발생 시 롤백")
    void syncToDb_RepositoryException() {
        Long memberId = 1L;
        Member member = mock(Member.class);
        Cart cart = mock(Cart.class);

        lenient().when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        lenient().when(cartRepository.findByMember_Id(memberId)).thenReturn(Optional.of(cart));
        given(cartItemRepository.findByCart_Member_Id(memberId)).willReturn(new ArrayList<>());
        given(cartItemRepository.saveAll(anyList())).willThrow(new RuntimeException("DB Error"));

        Map<Object, Object> redisMap = Map.of("100", "5");

        assertThatThrownBy(() -> cartService.syncToDb(memberId, redisMap))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("DB 동기화 - 파싱 에러 (무시)")
    void syncToDb_ParsingError() {
        Long memberId = 1L;
        lenient().when(memberRepository.findById(memberId)).thenReturn(Optional.of(new Member()));
        lenient().when(cartRepository.findByMember_Id(memberId)).thenReturn(Optional.of(new Cart(new Member())));

        // DB는 비어있다고 가정
        given(cartItemRepository.findByCart_Member_Id(memberId)).willReturn(new ArrayList<>());

        Map<Object, Object> redisMap = new HashMap<>();
        redisMap.put("bad_key", "1"); // 키 에러
        redisMap.put("100", "bad");   // 값 에러

        cartService.syncToDb(memberId, redisMap);

        verify(cartItemRepository, never()).saveAll(any());
    }

    // =================================================================
    // 6. 기타
    // =================================================================

    @Test
    @DisplayName("로그인 시 복구 - 데이터 없으면 스킵")
    void restoreCartOnLogin_NoData() {
        given(cartItemRepository.findByCart_Member_Id(1L)).willReturn(Collections.emptyList());
        cartService.restoreCartOnLogin(1L);
        verify(redisTemplate, never()).opsForHash();
    }
}