package com.nhnacademy.member_server.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Entity
@Data
@RequiredArgsConstructor
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int quantity;

    @ManyToOne
    private Cart cart;

    private Long bookId;

    public CartItem(Long bookId, int quantity, Cart cart){
        this.bookId = bookId;
        this.quantity = quantity;
        this.cart = cart;
    }

}
