package com.nhnacademy.member_server.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Entity
@Getter
@Builder
public class Grade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @NotNull
    @Column(name = "grade_name")
    private String gradeName;

    @NotNull
    @Setter
    private int min;

    @Setter
    private int max;

    @NotNull
    @Setter
    @Column(name = "point_rate")
    private BigDecimal pointRate;

}
