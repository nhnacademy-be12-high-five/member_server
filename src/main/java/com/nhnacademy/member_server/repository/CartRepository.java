package com.nhnacademy.member_server.repository;

import com.nhnacademy.member_server.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByMember_MemberId(Long memberId);
    Optional<Cart> findGuestCartById(Long guestCartId);
}
