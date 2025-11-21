package com.nhnacademy.member_server.repository;

import com.nhnacademy.member_server.entity.Cart;
import com.nhnacademy.member_server.entity.CartItem;
import feign.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByCartIdAndBookId(Long cartId, Long bookId);

    List<CartItem> findAllByCartId(Long cartId);

    // n+1 성능 문제 발생 -> 지울 대상을 전부 Select로 조회해옴 그 다음 하나씩 Delete 쿼리를 날림 그럼 조회 1 삭제 100 총 101번 날라감
    // 그렇기 때문에 modifying + query 사용 -> bulk 설정이라고 함 -> 이것 때문에 clearAutomatically 사용
    // 왜냐 영속성 컨텍스트에서 다시 참조하려 하기 때문에 싹 비우고 db 에서 가져오라 해야함
    @Modifying(clearAutomatically = true) // ★ 필수 설정
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId")
    void deleteByCartId(Long cartId);

    // 단건 삭제 -> 성능 위해 이것도 Modifying 사용
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.bookId = :bookId")
    void deleteByCartIdAndBookId(@Param("cartId") Long cartId, @Param("bookId") Long bookId);

}
