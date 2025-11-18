package com.nhnacademy.member_server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Cart {
    @Id
    private Long id;

    // @OneToOne
    // User Long user_id;
}
