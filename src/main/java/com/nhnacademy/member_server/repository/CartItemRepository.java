package com.nhnacademy.member_server.repository;

import com.nhnacademy.member_server.entity.cartEntity.CartItem;
import feign.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    void deleteAllByCartId(Long id);

    List<CartItem> findByCart_Member_Id(Long memberId);
}
