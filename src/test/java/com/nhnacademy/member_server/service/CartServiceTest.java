package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.dto.cartRequest.CartAddRequest;
import com.nhnacademy.member_server.dto.cartRequest.CartItemUpdateRequest;
import com.nhnacademy.member_server.dto.cartResponse.CartAddResponse;
import com.nhnacademy.member_server.dto.cartResponse.CartListResponse;
import com.nhnacademy.member_server.dto.cartResponse.GetBookResponse;
import com.nhnacademy.member_server.entity.Member;
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

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private BookFeignClient bookFeignClient;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private MemberRepository memberRepository;

    // Redis Operations Mock (RedisTemplate 내부 동작 모방용)
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private SetOperations<String, Object> setOperations;

    @BeforeEach
    void setUp() {
        // RedisTemplate이 호출될 때 우리가 만든 Mock Operation을 반환하도록 설정
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    @DisplayName("장바구니 담기 - 회원인 경우 Dirty Set에 추가되어야 한다")
    void addToCart_Member() {
        // given
        Long memberId = 1L;
        CartAddRequest request = new CartAddRequest(100L, 2);
        String key = "cart:m:" + memberId;

        // when
        CartAddResponse response = cartService.addToCart(request, memberId, null);

        // then
        assertThat(response.bookId()).isEqualTo(100L);
        assertThat(response.quantity()).isEqualTo(2);

        // Redis 명령어가 호출되었는지 검증
        verify(hashOperations).increment(eq(key), eq("100"), eq(2L)); // 수량 증가
        verify(redisTemplate).expire(eq(key), eq(7L), eq(TimeUnit.DAYS)); // 만료 시간 설정
        verify(setOperations).add(eq("cart:dirty"), eq("1")); // Dirty Set 추가 확인
    }

    @Test
    @DisplayName("장바구니 조회 - Redis에 데이터가 있으면 Feign으로 책 정보를 가져와 계산한다")
    void getCartItemList_FromRedis() {
        // given
        Long memberId = 1L;
        String key = "cart:m:" + memberId;

        // Redis Mock Data (책ID: 100, 수량: 2)
        Map<Object, Object> redisData = new HashMap<>();
        redisData.put("100", 2);
        given(hashOperations.entries(key)).willReturn(redisData);

        // Feign Mock Data
        GetBookResponse bookResponse = new GetBookResponse(100L, "테스트 책",10000, "img.jpg");
        given(bookFeignClient.getBooksBulk(anyList())).willReturn(List.of(bookResponse));

        // when
        CartListResponse result = cartService.getCartItemList(memberId, null);

        // then
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).title()).isEqualTo("테스트 책");
        assertThat(result.items().get(0).totalPrice()).isEqualTo(20000); // 10000 * 2
        assertThat(result.totalCartPrice()).isEqualTo(20000);
    }

    @Test
    @DisplayName("장바구니 조회 - Redis가 비어있으면 DB에서 복구를 시도한다")
    void getCartItemList_RestoreFromDb() {
        // given
        Long memberId = 1L;
        String key = "cart:m:" + memberId;

        // 1. Redis는 비어있음
        given(hashOperations.entries(key)).willReturn(Collections.emptyMap());

        // 2. DB에는 데이터가 있음
        Member member = new Member();
        Cart cart = new Cart(member);
        CartItem dbItem = new CartItem(100L, 5, cart); // 책 100번, 5권
        given(cartItemRepository.findByCart_Member_Id(memberId)).willReturn(List.of(dbItem));

        // 3. Feign Mock
        GetBookResponse bookResponse = new GetBookResponse(100L, "테스트 책",10000, "img.jpg");
        given(bookFeignClient.getBooksBulk(anyList())).willReturn(List.of(bookResponse));

        // when
        CartListResponse result = cartService.getCartItemList(memberId, null);

        // then
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).quantity()).isEqualTo(5);

        // 중요: DB 데이터를 Redis로 다시 넣었는지 검증 (Restore)
        verify(hashOperations).putAll(eq(key), anyMap());
    }

    @Test
    @DisplayName("수량 변경 - 성공 시 Redis 업데이트 및 Dirty Checking")
    void updateQuantity() {
        // given
        Long memberId = 1L;
        CartItemUpdateRequest request = new CartItemUpdateRequest(100L, 3);
        String key = "cart:m:" + memberId;

        given(hashOperations.hasKey(key, "100")).willReturn(true);

        // when
        cartService.updateCartItemQuantity(memberId, null, request);

        // then
        verify(hashOperations).put(key, "100", "3"); // 값 덮어쓰기
        verify(setOperations).add("cart:dirty", "1"); // Dirty Set 추가
    }

    @Test
    @DisplayName("DB 동기화 (syncToDb) - 기존 데이터를 지우고 Redis 데이터를 Insert 한다")
    void syncToDb() {
        // given
        Long memberId = 1L;
        Member member = new Member(); // 적절한 멤버 객체 생성
        Cart cart = new Cart(member);

        // Mocking
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(cartRepository.findByMember_Id(memberId)).willReturn(Optional.of(cart));

        // Redis에서 넘어온 데이터 (책 100번: 2권, 책 200번: 1권)
        Map<Object, Object> redisItems = new HashMap<>();
        redisItems.put("100", "2");
        redisItems.put("200", "1");

        // when
        cartService.syncToDb(memberId, redisItems);

        // then (호출 순서 검증)
        verify(cartItemRepository).deleteAllByCartId(cart.getId()); // 1. 삭제
        verify(cartItemRepository).flush(); // 2. Flush (중요!)
        verify(cartItemRepository).saveAll(anyList()); // 3. 저장
    }

    @Test
    @DisplayName("장바구니 합치기 (migrate) - 비회원 데이터를 회원 Key로 옮기고 비회원 Key 삭제")
    void migrateGuestCart() {
        // given
        String guestId = "guest-123";
        Long memberId = 1L;
        String guestKey = "cart:g:" + guestId;
        String memberKey = "cart:m:" + memberId;

        // 비회원 장바구니 데이터
        Map<Object, Object> guestItems = new HashMap<>();
        guestItems.put("100", 2);
        guestItems.put("200", 3);

        given(redisTemplate.hasKey(guestKey)).willReturn(true);
        given(hashOperations.entries(guestKey)).willReturn(guestItems);
        // 회원 키는 이미 존재한다고 가정 (hasKey -> true)
        given(redisTemplate.hasKey(memberKey)).willReturn(true);

        // when
        cartService.migrateGuestCart(guestId, memberId);

        // then
        // 1. 회원 키로 데이터가 병합(increment) 되었는지 확인
        verify(hashOperations).increment(memberKey, "100", 2);
        verify(hashOperations).increment(memberKey, "200", 3);

        // 2. 비회원 키가 삭제되었는지 확인
        verify(redisTemplate).delete(guestKey);

        // 3. Dirty Set에 추가되었는지 확인
        verify(setOperations).add("cart:dirty", String.valueOf(memberId));
    }

    @Test
    @DisplayName("DB 동기화 실패 - 회원이 존재하지 않으면 에러 로그 찍고 중단 (예외 발생 X)")
    void syncToDb_MemberNotFound() {
        // given
        Long memberId = 999L;
        given(memberRepository.findById(memberId)).willReturn(Optional.empty()); // 회원 없음

        // when
        cartService.syncToDb(memberId, new HashMap<>());

        // then
        // saveAll 등이 호출되지 않아야 함
        verify(cartRepository, never()).save(any());
        verify(cartItemRepository, never()).saveAll(any());
    }
}