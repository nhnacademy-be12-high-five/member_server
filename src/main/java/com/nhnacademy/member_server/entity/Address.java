package com.nhnacademy.member_server.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@Getter
public class Address {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private long id;

    @Setter
    private String alias;

    @Setter
    @Column(name = "road_address")
    private String roadAddress;

    @Setter
    @Column(name = "detail_address")
    private String detailAddress;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private Member member;
}
