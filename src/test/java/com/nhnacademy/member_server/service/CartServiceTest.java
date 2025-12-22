//package com.nhnacademy.member_server.service;
//
//import com.nhnacademy.member_server.dto.cartRequest.CartAddRequest;
//import com.nhnacademy.member_server.dto.cartRequest.CartItemUpdateRequest;
//import com.nhnacademy.member_server.dto.cartResponse.CartAddResponse;
//import com.nhnacademy.member_server.dto.cartResponse.CartUpdateResponse;
//import com.nhnacademy.member_server.exception.BusinessException;
//import com.nhnacademy.member_server.exception.ErrorCode;
//import com.nhnacademy.member_server.repository.CartItemRepository;
//import com.nhnacademy.member_server.repository.CartRepository;
//import com.nhnacademy.member_server.repository.MemberRepository;
//import com.nhnacademy.member_server.service.impl.CartServiceImpl;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.data.redis.core.HashOperations;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.data.redis.core.script.DefaultRedisScript;
//
//import java.util.List;
//
//import static org.assertj.core.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class CartServiceTest {
//
//    @InjectMocks
//    CartServiceImpl cartService;
//
//    @Mock RedisTemplate<String, Object> redisTemplate;
//    @Mock RedisTemplate<String, Object> luaRedisTemplate;
//
//    @Mock DefaultRedisScript<Long> cartUpsertScript;
//    @Mock DefaultRedisScript<Long> cartMergeScript;
//
//    @Mock HashOperations<String, Object, Object> hashOperations;
//
//    @Mock
//    CartItemRepository cartItemRepository;
//    @Mock
//    CartRepository cartRepository;
//    @Mock
//    MemberRepository memberRepository;
//
//    @BeforeEach
//    void setUp() {
//        lenient().when(redisTemplate.opsForHash())
//                .thenReturn(hashOperations);
//
//        // Lua는 전부 느슨하게
//        lenient().doReturn(1L)
//                .when(luaRedisTemplate)
//                .execute(any(), anyList(), any());
//
//        lenient().doReturn(true)
//                .when(redisTemplate)
//                .hasKey(anyString());
//    }
//
//    // ===================== ADD =====================
//
//    @Test
//    @DisplayName("장바구니 추가 성공")
//    void addToCart_success() {
//        CartAddRequest req = new CartAddRequest(100L, 1);
//
//        CartAddResponse res = cartService.addToCart(req, 1L, null);
//
//        assertThat(res.bookId()).isEqualTo(100L);
//        assertThat(res.quantity()).isEqualTo(1);
//    }
//
//    @Test
//    @DisplayName("장바구니 추가 - Lua 결과 INVALID")
//    void addToCart_invalidQuantity() {
//        lenient().doReturn(-1L)
//                .when(luaRedisTemplate)
//                .execute(any(), anyList(), any());
//
//        CartAddRequest req = new CartAddRequest(100L, 999);
//
//        assertThatThrownBy(() -> cartService.addToCart(req, 1L, null))
//                .isInstanceOf(BusinessException.class)
//                .extracting("errorCode")
//                .isEqualTo(ErrorCode.INVALID_QUANTITY);
//    }
//
//    // ===================== UPDATE =====================
//
//    @Test
//    @DisplayName("수량 변경 성공")
//    void updateQuantity_success() {
//        lenient().when(hashOperations.hasKey(anyString(), any()))
//                .thenReturn(true);
//
//        CartItemUpdateRequest req = new CartItemUpdateRequest(100L, 5);
//
//        CartUpdateResponse res =
//                cartService.updateCartItemQuantity(1L, null, req);
//
//        assertThat(res.quantity()).isEqualTo(5);
//    }
//
//    @Test
//    @DisplayName("수량 변경 - 상품 없음")
//    void updateQuantity_notFound() {
//        lenient().when(hashOperations.hasKey(anyString(), any()))
//                .thenReturn(false);
//
//        CartItemUpdateRequest req = new CartItemUpdateRequest(100L, 5);
//
//        assertThatThrownBy(() ->
//                cartService.updateCartItemQuantity(1L, null, req))
//                .isInstanceOf(BusinessException.class)
//                .extracting("errorCode")
//                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
//    }
//
//    // ===================== GUEST MERGE =====================
//
//    @Test
//    @DisplayName("게스트 카트 병합 성공")
//    void migrateGuestCart_success() {
//        lenient().when(cartItemRepository.findByCart_Member_Id(any()))
//                .thenReturn(List.of());
//
//        cartService.migrateGuestCart("guest", 1L);
//
//        verify(luaRedisTemplate)
//                .execute(any(), anyList(), any());
//    }
//}
