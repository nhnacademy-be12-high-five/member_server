package com.nhnacademy.member_server.repository;

import com.nhnacademy.member_server.entity.cartEntity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCart_Member_Id(Long memberId);

    void deleteByCart_Member_Id(Long memberId);
}
