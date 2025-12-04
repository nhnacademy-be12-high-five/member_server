package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.dto.cartRequest.CartAddRequest;
import com.nhnacademy.member_server.dto.cartRequest.CartItemUpdateRequest;
import com.nhnacademy.member_server.dto.cartResponse.CartAddResponse;
import com.nhnacademy.member_server.dto.cartResponse.CartDetailResponse;
import com.nhnacademy.member_server.dto.cartResponse.CartListResponse;
import com.nhnacademy.member_server.dto.cartResponse.GetBookResponse;
import com.nhnacademy.member_server.entity.cartEntity.Cart;
import com.nhnacademy.member_server.entity.cartEntity.CartItem;
import com.nhnacademy.member_server.feign.BookFeignClient;
import com.nhnacademy.member_server.repository.CartItemRepository;
import com.nhnacademy.member_server.repository.CartRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.service.impl.CartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @InjectMocks
    private CartServiceImpl cartService;

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private BookFeignClient bookFeignClient;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private CartRepository cartRepository;
    @Mock private MemberRepository memberRepository;

    // Redis Operation Mocks (RedisTemplate 내부 동작 모방용)
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private SetOperations<String, Object> setOperations;

    private static final String DIRTY_KEY = "cart:dirty";

    @BeforeEach
    void setUp() {
        // RedisTemplate이 호출될 때 우리가 만든 가짜 Operation 객체를 반환하도록 설정
        // lenient()를 써서 일부 테스트에서 사용 안 해도 에러 안 나게 함
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    @DisplayName("장바구니 담기 - 회원 (Redis 저장 + Dirty Bit 설정)")
    void addToCart_Member() {
        // given
        Long memberId = 1L;
        CartAddRequest request = new CartAddRequest(100L, 2);

        // when
        CartAddResponse response = cartService.addToCart(request, memberId, null);

        // then
        assertThat(response.bookId()).isEqualTo(100L);

        // 1. Redis Hash 증가 확인
        verify(hashOperations).increment(eq("cart:m:1"), eq("100"), eq(2L));
        // 2. 만료 시간 설정 확인
        verify(redisTemplate).expire(eq("cart:m:1"), eq(7L), eq(TimeUnit.DAYS));
        // 3. Dirty Set 추가 확인 (회원이므로)
        verify(setOperations).add(DIRTY_KEY, "1");
    }

    @Test
    @DisplayName("장바구니 담기 - 비회원 (Redis 저장만, Dirty Bit X)")
    void addToCart_Guest() {
        // given
        String guestId = "guest-123";
        CartAddRequest request = new CartAddRequest(100L, 1);

        // when
        cartService.addToCart(request, null, guestId);

        // then
        verify(hashOperations).increment(eq("cart:g:guest-123"), eq("100"), eq(1L));
        verify(setOperations, never()).add(anyString(), anyString()); // 비회원은 DB 동기화 안함
    }

    @Test
    @DisplayName("장바구니 목록 조회 - Redis 히트 & Feign Client 연동")
    void getCartItemList_RedisHit() {
        // given
        Long memberId = 1L;
        String key = "cart:m:1";

        // Redis에 저장된 데이터 모의 (BookId: "100", Quantity: "2")
        Map<Object, Object> redisData = new HashMap<>();
        redisData.put("100", "2");

        given(hashOperations.entries(key)).willReturn(redisData);

        // Feign Client가 리턴할 책 정보
        GetBookResponse bookInfo = new GetBookResponse(100L, "Java 정석", 20000, "img.jpg");
        given(bookFeignClient.getBooksBulk(List.of(100L))).willReturn(List.of(bookInfo));

        // when
        CartListResponse result = cartService.getCartItemList(memberId, null);

        // then
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).title()).isEqualTo("Java 정석");
        assertThat(result.items().get(0).quantity()).isEqualTo(2); // Redis 수량
        assertThat(result.totalCartPrice()).isEqualTo(40000L); // 20000 * 2
    }

    @Test
    @DisplayName("장바구니 목록 조회 - Redis 미스 -> DB에서 복구(Restore)")
    void getCartItemList_RedisMiss_RestoreFromDb() {
        // given
        Long memberId = 1L;
        String key = "cart:m:1";

        // 1. Redis는 비어있음
        given(hashOperations.entries(key)).willReturn(Collections.emptyMap());

        // 2. DB에는 데이터가 있음
        CartItem dbItem = new CartItem(100L, 5, new Cart(null)); // Mock Entity
        given(cartItemRepository.findByCart_Member_Id(memberId)).willReturn(List.of(dbItem));

        // 3. Feign Mock
        GetBookResponse bookInfo = new GetBookResponse(100L, "Java 정석", 20000, "img.jpg");
        given(bookFeignClient.getBooksBulk(List.of(100L))).willReturn(List.of(bookInfo));

        // when
        CartListResponse result = cartService.getCartItemList(memberId, null);

        // then
        // 복구 로직이 실행되었는지 검증 (putAll 호출 여부)
        verify(hashOperations).putAll(eq(key), anyMap());
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).quantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("수량 변경")
    void updateCartItemQuantity() {
        // given
        Long memberId = 1L;
        CartItemUpdateRequest request = new CartItemUpdateRequest(100L, 10);
        String key = "cart:m:1";

        given(hashOperations.hasKey(key, "100")).willReturn(true);

        // when
        cartService.updateCartItemQuantity(memberId, null, request);

        // then
        verify(hashOperations).put(key, "100", "10"); // 덮어쓰기 확인
        verify(setOperations).add(DIRTY_KEY, "1");
    }

    @Test
    @DisplayName("단건 삭제")
    void deleteCartItem() {
        // given
        Long memberId = 1L;
        Long bookId = 100L;

        // when
        cartService.deleteCartItem(memberId, null, bookId);

        // then
        verify(hashOperations).delete("cart:m:1", "100");
        verify(setOperations).add(DIRTY_KEY, "1");
    }

    @Test
    @DisplayName("전체 삭제")
    void deleteAllCartItem() {
        // given
        Long memberId = 1L;

        given(redisTemplate.delete("cart:m:1")).willReturn(true);

        // when
        cartService.deleteAllCartItem(memberId, null);

        // then
        verify(redisTemplate).delete("cart:m:1");
        verify(setOperations).add(DIRTY_KEY, "1");
    }

    @Test
    @DisplayName("비회원 장바구니 합치기 (Merge)")
    void migrateGuestCart() {
        // given
        String guestId = "guest-123";
        Long memberId = 1L;
        String guestKey = "cart:g:" + guestId;
        String memberKey = "cart:m:" + memberId;

        // 게스트 장바구니 내용물
        Map<Object, Object> guestItems = new HashMap<>();
        guestItems.put("100", "2"); // 책 100번 2권
        guestItems.put("200", "1"); // 책 200번 1권

        given(redisTemplate.hasKey(guestKey)).willReturn(true);
        given(redisTemplate.hasKey(memberKey)).willReturn(true); // 회원 키도 이미 있다고 가정
        given(hashOperations.entries(guestKey)).willReturn(guestItems);

        // when
        cartService.migrateGuestCart(guestId, memberId);

        // then
        // 회원 키로 increment가 호출되어야 함 (합치기)
        verify(hashOperations).increment(memberKey, "100", 2);
        verify(hashOperations).increment(memberKey, "200", 1);

        // 게스트 키 삭제 확인
        verify(redisTemplate).delete(guestKey);
        // Dirty Set 추가 확인
        verify(setOperations).add(DIRTY_KEY, "1");
    }

    @Test
    @DisplayName("DB 동기화 (SyncToDb) - 핵심 로직")
    void syncToDb() {
        // given
        Long memberId = 1L;
        // Redis에서 읽어온 데이터라고 가정
        Map<Object, Object> redisItems = new HashMap<>();
        redisItems.put("100", "5");
        redisItems.put("101", "3");

        Cart mockCart = new Cart(null);
        // ID 강제 주입 (Reflection 혹은 Setter 필요하지만 여기선 Mock킹으로 커버)
        // 실제로는 repository.save()가 Cart를 반환함
        given(cartRepository.findByMember_Id(memberId)).willReturn(Optional.of(mockCart));

        // when
        cartService.syncToDb(memberId, redisItems);

        // then
        // 1. 기존 아이템 삭제 호출 확인
        verify(cartItemRepository).deleteAllByCartId(any());
        // 2. 새로운 아이템 저장 호출 확인
        verify(cartItemRepository).saveAll(anyList());
    }
}