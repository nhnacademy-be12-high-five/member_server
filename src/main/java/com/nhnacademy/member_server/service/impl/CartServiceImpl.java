package com.nhnacademy.member_server.service.impl;

import com.nhnacademy.member_server.cache.BookCacheService;
import com.nhnacademy.member_server.dto.CartAddRequest;
import com.nhnacademy.member_server.dto.CartDetailResponse;
import com.nhnacademy.member_server.dto.CartListResponse;
import com.nhnacademy.member_server.entity.Cart;
import com.nhnacademy.member_server.entity.CartItem;
import com.nhnacademy.member_server.feign.BookFeignClient;
import com.nhnacademy.member_server.repository.CartItemRepository;
import com.nhnacademy.member_server.repository.CartRepository;
import com.nhnacademy.member_server.service.CartService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final BookCacheService bookCacheService;

    @Override
    public CartListResponse getCartItemList(HttpServletRequest request){
        Cart cart = null;
        Long memberId = getMemberId();

        if(memberId != null){
            cart = cartRepository.findByMemberId(memberId).orElse(null);
        }else{
            Cookie guestCookie = findCookie(request);
            if(guestCookie != null){
                try{
                    Long guestCartId = Long.parseLong(guestCookie.getValue());
                    cart = cartRepository.findGuestCartById(guestCartId).orElse(null);
                } catch (NumberFormatException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        if(cart == null){
            return new CartListResponse(Collections.emptyList(), 0L);
        }

        List<CartItem> cartItems = cartItemRepository.findAllByCartId(cart);

        List<Long> bookIds = cartItems.stream()
                .map(CartItem::getBookId)
                .toList();

        List<CartDetailResponse> bookInfoList = bookCacheService.getBooksBulk(bookIds);

        // Map<BookId, BookResponse> 생성
        Map<Long, CartDetailResponse> bookMap = bookInfoList.stream()
                .collect(Collectors.toMap(CartDetailResponse::id, b -> b));

        long totalCartPrice = 0L;
        List<CartDetailResponse> responseList = new ArrayList<>();

        for(CartItem item : cartItems){
            CartDetailResponse book = bookMap.get(item.getBookId());

            long itemTotalPrice = book.price() * item.getQuantity();
            totalCartPrice += itemTotalPrice;

            responseList.add(new CartDetailResponse(
                    book.id(),
                    book.title(),
                    book.author(),
                    book.price(),
                    item.getQuantity(),
                    itemTotalPrice,
                    book.image()
            ));}

        return new CartListResponse(responseList, totalCartPrice);
    }

    @Override
    public void addBookToCart(CartAddRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse){
        Cart cart = getCurrentCart(httpRequest, httpResponse);

        Optional<CartItem> existCartItem = cartItemRepository.findByCartIdAndBookId(cart, request.bookId());

        if (existCartItem.isPresent()) {
            // 존재하는 상품
            CartItem existingItem = existCartItem.get();

            // 기존 수량 + 요청 수량
            // (최대 수량 제한 로직을 여기에 추가할 수 있습니다. 예: 10권 제한)
            existingItem.setQuantity(existingItem.getQuantity() + request.quantity());

        } else {
            CartItem newItem = new CartItem(request.bookId(), request.quantity());

            cartItemRepository.save(newItem);
        }
    }


    // 회원이면 repository 에서 해당 Cart 찾아서 반환
    // 비회원이면 repository 에서 새로운 Cart 생성

    private Cart getCurrentCart(HttpServletRequest request, HttpServletResponse response){
        Long userId = this.getMemberId();
        if(userId != null){
            return (Cart) cartRepository.findByMemberId(userId)
                    .orElseGet(() -> createCart(userId));
        }
        Cookie guestCookie = findCookie(request);
        if(guestCookie != null){
            long guestCartId;
            try{
                guestCartId = Long.parseLong(guestCookie.getValue());
            }catch(NumberFormatException e){
                return createGuestCartAndSetCookie(response);
            }

            Optional<Cart> existCart = cartRepository.findByIdAndMemberIdIsNull(guestCartId);

            if(existCart.isPresent()){
                return existCart.get();
            }
        }
        return createGuestCartAndSetCookie(response);
    }

    private Cart createCart(Long userId){
        return cartRepository.save(new Cart(userId));
    }

    private Cart createGuestCartAndSetCookie(HttpServletResponse response){
        Cart newCart = new Cart(null);
        newCart = cartRepository.save(newCart);

        Cookie newCookie = new Cookie("guestCookie", newCart.getId().toString());
        newCookie.setPath("/");
        newCookie.setMaxAge(60 * 60 * 24 * 30);
        response.addCookie(newCookie);

        return newCart;
    }

    private Cookie findCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie.getName().equals("guestCookie")) {
                    return cookie;
                }
            }
        }
        return null;
    }

    private Long getMemberId(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)){
            String userId = ((UserDetails) auth.getPrincipal()).getUsername();

            return Long.parseLong(userId);
        }
        return null;
    }
}
