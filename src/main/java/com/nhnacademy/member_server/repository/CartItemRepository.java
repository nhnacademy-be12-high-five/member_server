package com.nhnacademy.member_server.repository;

import com.nhnacademy.member_server.entity.cartEntity.CartItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    @Modifying(clearAutomatically = true) // 1차 캐시 정리 필수
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId")
    void deleteAllByCartId(Long id);

    List<CartItem> findByCart_Member_Id(Long memberId);
}
