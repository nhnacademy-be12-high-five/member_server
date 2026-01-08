package com.nhnacademy.member_server.entity.member;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "grade")
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
    @Column(name = "point_rate", precision = 4, scale = 3)
    private BigDecimal pointRate;
}
