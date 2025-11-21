package com.nhnacademy.member_server.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY) // 지연 로딩 필수!
    @JoinColumn(name = "member_id")
    private Member member;

    public Cart(Member member) {
        this.member = member;
    }
}
