package com.nhnacademy.member_server.entity;

import com.nhnacademy.member_server.entity.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "point_event_type", length = 50)
    private PointEventType pointEventType;

    @Column(nullable = false)
    private Long pointBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointStatus status; // RESERVED, CONFIRMED, CANCELED

    private Long originalPointHistoryId;

    @Builder
    public PointHistory(Long orderId, Member member, Long amount, String description, PointEventType pointEventType,
                        Long pointBalance, PointStatus status, Long originalPointHistoryId) {
        this.orderId = orderId;
        this.member = member;
        this.amount = amount;
        this.description = description;
        this.pointEventType = pointEventType;
        this.pointBalance = pointBalance;
        this.status = (status != null) ? status : PointStatus.CONFIRMED;
        this.originalPointHistoryId = originalPointHistoryId;
    }

    public void updateStatus(PointStatus status) {
        this.status = status;
    }
}
