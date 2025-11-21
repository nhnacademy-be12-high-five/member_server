package com.nhnacademy.member_server;


import com.nhnacademy.member_server.service.impl.CartServiceImpl;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import com.nhnacademy.member_server.dto.CartAddRequest;
import com.nhnacademy.member_server.dto.CartDetailResponse;
import com.nhnacademy.member_server.dto.CartItemUpdateRequest;
import com.nhnacademy.member_server.dto.CartListResponse;
import com.nhnacademy.member_server.entity.Cart;
import com.nhnacademy.member_server.entity.CartItem;
import com.nhnacademy.member_server.entity.Member;
import com.nhnacademy.member_server.exception.BusinessException;
import com.nhnacademy.member_server.exception.ErrorCode;
import com.nhnacademy.member_server.feign.BookFeignClient;
import com.nhnacademy.member_server.repository.CartItemRepository;
import com.nhnacademy.member_server.repository.CartRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @InjectMocks
    private CartServiceImpl cartService;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private BookFeignClient bookFeignClient;

    @Mock
    private MemberRepository memberRepository;

    // =================================================================
    // 1. 장바구니 담기 (addBookToCart)
    // =================================================================

    @Test
    @DisplayName("회원 - 장바구니에 새 상품 추가")
    void addBookToCart_Member_NewItem() {
        // given
        Long memberId = 1L;
        CartAddRequest request = new CartAddRequest(100L, 2); // 책ID 100, 수량 2

        // Mock: 회원 카트가 이미 존재한다고 가정
        Cart mockCart = new Cart(new Member());
        ReflectionTestUtils.setField(mockCart, "id", 10L); // ID 강제 주입
        given(cartRepository.findByMember_Id(memberId)).willReturn(Optional.of(mockCart));

        // Mock: 해당 상품은 장바구니에 없음
        given(cartItemRepository.findByCartIdAndBookId(10L, 100L)).willReturn(Optional.empty());

        // when
        String result = cartService.addBookToCart(request, memberId, null);

        // then
        assertThat(result).isNull(); // 회원은 쿠키 ID를 반환하지 않음
        verify(cartItemRepository).save(any(CartItem.class)); // 저장이 호출되었는지 검증
    }

    @Test
    @DisplayName("회원 - 이미 있는 상품 수량 증가")
    void addBookToCart_Member_ExistingItem() {
        // given
        Long memberId = 1L;
        CartAddRequest request = new CartAddRequest(100L, 3);

        Cart mockCart = new Cart(new Member());
        ReflectionTestUtils.setField(mockCart, "id", 10L);
        given(cartRepository.findByMember_Id(memberId)).willReturn(Optional.of(mockCart));

        // Mock: 이미 수량 2개인 아이템이 존재함
        CartItem existingItem = new CartItem(100L, 2, mockCart);
        given(cartItemRepository.findByCartIdAndBookId(10L, 100L)).willReturn(Optional.of(existingItem));

        // when
        cartService.addBookToCart(request, memberId, null);

        // then
        assertThat(existingItem.getQuantity()).isEqualTo(5); // 2 + 3 = 5
        verify(cartItemRepository, never()).save(any(CartItem.class)); // save는 호출 안 됨 (Dirty Checking)
    }

    @Test
    @DisplayName("비회원 - 첫 장바구니 생성 및 쿠키값 반환")
    void addBookToCart_Guest_FirstTime() {
        // given
        CartAddRequest request = new CartAddRequest(100L, 1);

        // Mock: 저장 시 새로운 카트(ID=555) 반환 설정
        Cart newCart = new Cart(null);
        ReflectionTestUtils.setField(newCart, "id", 555L);
        given(cartRepository.save(any(Cart.class))).willReturn(newCart);

        // when
        String resultCookie = cartService.addBookToCart(request, null, null);

        // then
        assertThat(resultCookie).isEqualTo("555"); // 쿠키로 쓸 ID가 반환되어야 함
        verify(cartItemRepository).save(any(CartItem.class));
    }

    // =================================================================
    // 2. 장바구니 조회 (getCartItemList)
    // =================================================================

    @Test
    @DisplayName("장바구니 조회 - 정상 케이스")
    void getCartItemList_Success() {
        // given
        Long memberId = 1L;
        Cart mockCart = new Cart(new Member());
        ReflectionTestUtils.setField(mockCart, "id", 10L);

        CartItem item1 = new CartItem(100L, 2, mockCart); // 책 100번, 2권
        CartItem item2 = new CartItem(200L, 1, mockCart); // 책 200번, 1권

        given(cartRepository.findByMember_Id(memberId)).willReturn(Optional.of(mockCart));
        given(cartItemRepository.findAllByCartId(10L)).willReturn(List.of(item1, item2));

        // Feign Client Mock
        List<CartDetailResponse> bookInfos = List.of(
                new CartDetailResponse(100L, "자바의 정석", "남궁성", 10000L, 0, 0L, "img1"),
                new CartDetailResponse(200L, "JPA 프로그래밍", "김영한", 20000L, 0, 0L, "img2")
        );
        given(bookFeignClient.getBooksBulk(anyList())).willReturn(bookInfos);

        // when
        CartListResponse response = cartService.getCartItemList(memberId, null);

        // then
        assertThat(response.items()).hasSize(2);
        assertThat(response.totalCartPrice()).isEqualTo(40000L); // (10000*2) + (20000*1)
    }

    @Test
    @DisplayName("장바구니 조회 - 빈 장바구니")
    void getCartItemList_Empty() {
        // given
        Long memberId = 1L;
        Cart mockCart = new Cart(new Member());
        ReflectionTestUtils.setField(mockCart, "id", 10L);

        given(cartRepository.findByMember_Id(memberId)).willReturn(Optional.of(mockCart));
        given(cartItemRepository.findAllByCartId(10L)).willReturn(List.of()); // 아이템 없음

        // when
        CartListResponse response = cartService.getCartItemList(memberId, null);

        // then
        assertThat(response.items()).isEmpty();
        assertThat(response.totalCartPrice()).isEqualTo(0L);
        verify(bookFeignClient, never()).getBooksBulk(anyList()); // Feign 호출 안 해야 함
    }

    // =================================================================
    // 3. 수량 변경 (updateCartItemQuantity)
    // =================================================================

    @Test
    @DisplayName("수량 변경 성공")
    void updateCartItemQuantity_Success() {
        // given
        Long memberId = 1L;
        CartItemUpdateRequest request = new CartItemUpdateRequest(100L, 5);

        Cart mockCart = new Cart(new Member());
        ReflectionTestUtils.setField(mockCart, "id", 10L);
        given(cartRepository.findByMember_Id(memberId)).willReturn(Optional.of(mockCart));

        CartItem mockItem = new CartItem(100L, 1, mockCart);
        given(cartItemRepository.findByCartIdAndBookId(10L, 100L)).willReturn(Optional.of(mockItem));

        // when
        cartService.updateCartItemQuantity(memberId, null, request);

        // then
        assertThat(mockItem.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("수량 변경 실패 - 아이템 없음")
    void updateCartItemQuantity_Fail_NotFound() {
        // given
        Long memberId = 1L;
        CartItemUpdateRequest request = new CartItemUpdateRequest(100L, 5);

        Cart mockCart = new Cart(new Member());
        ReflectionTestUtils.setField(mockCart, "id", 10L);
        given(cartRepository.findByMember_Id(memberId)).willReturn(Optional.of(mockCart));

        // 아이템이 없음
        given(cartItemRepository.findByCartIdAndBookId(10L, 100L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> cartService.updateCartItemQuantity(memberId, null, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    // =================================================================
    // 4. 삭제 (Delete)
    // =================================================================

    @Test
    @DisplayName("전체 삭제")
    void deleteAllCartItem() {
        // given
        Long memberId = 1L;
        Cart mockCart = new Cart(new Member());
        ReflectionTestUtils.setField(mockCart, "id", 10L);
        given(cartRepository.findByMember_Id(memberId)).willReturn(Optional.of(mockCart));

        // when
        cartService.deleteAllCartItem(memberId, null);

        // then
        verify(cartItemRepository).deleteByCartId(10L);
    }

    @Test
    @DisplayName("단건 삭제")
    void deleteOneCartItem() {
        // given
        Long memberId = 1L;
        Long bookId = 100L;

        Cart mockCart = new Cart(new Member());
        ReflectionTestUtils.setField(mockCart, "id", 10L);
        given(cartRepository.findByMember_Id(memberId)).willReturn(Optional.of(mockCart));

        // when
        cartService.deleteCartItem(memberId, null, bookId);

        // then
        verify(cartItemRepository).deleteByCartIdAndBookId(10L, bookId);
    }
}
