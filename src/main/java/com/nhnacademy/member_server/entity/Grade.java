package com.nhnacademy.member_server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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