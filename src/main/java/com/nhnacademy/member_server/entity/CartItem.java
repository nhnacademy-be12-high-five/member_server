package com.nhnacademy.member_server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class CartItem {
    @Id
    private Long id;

    private int quantity;

    @ManyToOne
    private Cart cart_id;

    // @ManyToOne
    // private Book book_id;

}
