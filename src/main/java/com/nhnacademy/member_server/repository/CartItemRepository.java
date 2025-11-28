package com.nhnacademy.member_server.repository;

import com.nhnacademy.member_server.entity.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByCartIdAndBookId(Long cartId, Long bookId);

    List<CartItem> findAllByCartId(Long cartId);

    void deleteByCartId(Long cartId);
}
