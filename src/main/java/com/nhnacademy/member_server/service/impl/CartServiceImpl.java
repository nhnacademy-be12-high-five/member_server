package com.nhnacademy.member_server.service.impl;

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
import com.nhnacademy.member_server.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final BookFeignClient bookFeignClient;
    private final MemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true) // 성능 최적화, 더티 체킹 과정을 생략함 -> 어차피 수정안하니까 스냅샷 안만듬 변경 감지 x
    public CartListResponse getCartItemList(Long memberId, String guestId) {
        Cart cart = findCart(memberId, guestId).orElse(null);

        // 카트가 없으면
        if (cart == null) {
            return new CartListResponse(Collections.emptyList(), 0L);
        }

        // 카트가 있는데 모두 비어있다면
        List<CartItem> cartItems = cartItemRepository.findAllByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            return new CartListResponse(Collections.emptyList(), 0L);
        }

        // Bulk 조회
        List<Long> bookIds = cartItems.stream().map(CartItem::getBookId).toList();
        List<CartDetailResponse> bookInfoList = bookFeignClient.getBooksBulk(bookIds);
        Map<Long, CartDetailResponse> bookMap = bookInfoList.stream()
                .collect(Collectors.toMap(CartDetailResponse::id, b -> b));

        long totalCartPrice = 0L;
        List<CartDetailResponse> responseList = new ArrayList<>();

        for (CartItem item : cartItems) {
            CartDetailResponse book = bookMap.get(item.getBookId());
            if (book == null) continue; // 책 정보가 없을 경우 대비

            long itemTotalPrice = book.price() * item.getQuantity();
            totalCartPrice += itemTotalPrice;

            responseList.add(new CartDetailResponse(
                    book.id(), book.title(), book.author(), book.price(),
                    item.getQuantity(), itemTotalPrice, book.image()
            ));
        }

        return new CartListResponse(responseList, totalCartPrice);
    }

    @Override
    public String addBookToCart(CartAddRequest request, Long memberId, String guestId) {
        // 카트 가져오기 (없으면 생성)
        Cart cart = resolveCart(memberId, guestId);

        // 아이템 추가/수정 로직
        Optional<CartItem> existCartItem = cartItemRepository.findByCartIdAndBookId(cart.getId(), request.bookId());
        // 카트 아이템 존재시 수량만 증가
        if (existCartItem.isPresent()) {
            CartItem existingItem = existCartItem.get();
            existingItem.setQuantity(existingItem.getQuantity() + request.quantity());
        }
        // 없다면 새롭게 추가
        else {
            cartItemRepository.save(new CartItem(request.bookId(), request.quantity(), cart));
        }

        // 3. 새로운 비회원 카트가 생성된 경우에만 ID 반환 controller 에서 쿠키 구워주기 위해서
        if (memberId == null && (!cart.getId().toString().equals(guestId))) {
            return cart.getId().toString();
        }
        return null;
    }

    // 장바구니 전체 비우기
    @Override
    public void deleteAllCartItem(Long memberId, String guestId) {
        findCart(memberId, guestId).ifPresent(cart ->
                cartItemRepository.deleteByCartId(cart.getId())
        );
    }

    // 장바구니 책 단건 삭제 (수량 무시하고)
    @Override
    public void deleteCartItem(Long memberId, String guestId, Long bookId) {
        findCart(memberId, guestId).ifPresent(cart -> {
            cartItemRepository.deleteByCartIdAndBookId(cart.getId(), bookId);
        });
    }

    // 장바구니에서 수량 변경
    @Override
    public void updateCartItemQuantity(Long memberId, String guestId, CartItemUpdateRequest request) {
        Cart cart = findCart(memberId, guestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

        CartItem item = cartItemRepository.findByCartIdAndBookId(cart.getId(), request.bookId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        item.setQuantity(request.quantity());
    }


    /// 보조 메소드들 (여기부터)

    // 카트를 찾거나, 없으면 생성해서 반환
    private Cart resolveCart(Long memberId, String guestId) {
        // 카트를 찾고 만약에 없다면 회원아이디로 카트를 생성해줌
        if (memberId != null) {
            return cartRepository.findByMember_Id(memberId)
                    .orElseGet(() -> createMemberCart(memberId));
        }

        // 비회원: 쿠키 ID로 조회 시도
        if (guestId != null) {
            try {
                Long id = Long.parseLong(guestId);
                return cartRepository.findGuestCartById(id)
                        .orElseGet(() -> cartRepository.save(new Cart(null))); // 유효하지 않은 쿠키면 새로 생성, 비회원은 memberId가 null
            } catch (NumberFormatException e) {
                // 쿠키 값이 이상하면 무시하고 새로 생성
            }
        }

        // 쿠키도 없고 회원도 아니면 새로 생성
        return cartRepository.save(new Cart(null));
    }

    // 멤버의 카트를 생성해주는 함수
    private Cart createMemberCart(Long memberId) {
        Member memberRef = memberRepository.getReferenceById(memberId);

        Cart newCart = new Cart(memberRef); // Member 객체를 넣어줌!
        return cartRepository.save(newCart);
    }

    // 단순 조회용 (생성 X)
    private Optional<Cart> findCart(Long memberId, String guestId) {
        if (memberId != null) {
            return cartRepository.findByMember_Id(memberId);
        }
        if (guestId != null) {
            try {
                return cartRepository.findGuestCartById(Long.parseLong(guestId));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}