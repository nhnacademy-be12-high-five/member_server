package com.nhnacademy.member_server.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "address_id")
    private Long id;

    @Setter
    @Column(length = 50)
    private String alias;

    @Setter
    @Column(name = "road_address", length = 255)
    private String roadAddress;

    @Setter
    @Column(name = "detail_address", length = 255)
    private String detailAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;
}