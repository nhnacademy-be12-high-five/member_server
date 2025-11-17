package com.nhnacademy.member_server.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;


//TODO#1-1 먼저 제일 의존성이 적은 entity + repository 생성
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Entity
@Builder
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @NotNull
    @Column(name = "login_id")
    private String loginId;

    @Setter
    @NotNull
    private String name;

    @Setter
    @NotNull
    private String password;

    @Setter
    @NotNull
    private String phone;

    @Setter
    @NotNull
    private String email;

    @Setter
    @Column(name = "birth_date")
    private Timestamp birthDate;

    @Setter
    @NotNull
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;


    @Setter
    @NotNull
    @Enumerated(EnumType.STRING)
    private Status status;

    @Setter
    private String providerId;

    @Setter
    private String provider;

    @Setter
    @NotNull
    private String role;

    @Setter
    @NotNull
    @Column(name = "current_point")
    private long currentPoint;

    @ManyToOne
    @JoinColumn(name = "grade_id")
    private Grade grade;
}
