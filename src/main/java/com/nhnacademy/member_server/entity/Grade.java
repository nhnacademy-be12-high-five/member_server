package com.nhnacademy.member_server.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Grade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "grade_id")
    private Long id;

    @NotNull
    @Column(name = "grade_name", length = 50)
    private String gradeName;

    @NotNull
    @Setter
    private int min;

    @Setter
    private Integer max;

    @NotNull
    @Setter
    @Column(name = "point_rate", precision = 3, scale = 2)
    private BigDecimal pointRate;
}