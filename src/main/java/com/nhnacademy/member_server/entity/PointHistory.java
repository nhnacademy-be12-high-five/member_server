package com.nhnacademy.member_server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "point_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class PointHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private Long amount;

    @CreatedDate // 포인트 변동 시간 자동 저장
    @Column(nullable = false, updatable = false) // 최초 저장 후 수정 x
    private LocalDateTime createdAt;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false)
    private Long pointBalance;

    public PointHistory(Long orderId, Member member, Long amount, String description, Long pointBalance) {
        this.orderId = orderId;
        this.member = member;
        this.amount = amount;
        this.description = description;
        this.pointBalance = pointBalance;

        this.createdAt = LocalDateTime.now(); // 원래 자동생성인데 Hibernate가 널체크 해서 임시용
    }
}
