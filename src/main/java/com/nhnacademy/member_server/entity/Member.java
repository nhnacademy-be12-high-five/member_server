package com.nhnacademy.member_server.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "Member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @NotNull
    @Column(name = "login_id", unique = true, nullable = false, length = 50)
    private String loginId;

    @Setter
    @NotNull    @Column(nullable = false, length = 50)
    private String name;

    @Setter
    @NotNull    @Column(nullable = false)
    private String password;

    @Setter
    @NotNull    @Column(nullable = false, length = 20)
    private String phone;

    @Setter
    @NotNull    @Column(nullable = false, length = 100)
    private String email;

    @Setter
    @NotNull    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Setter
    @NotNull    @Column(name = "last_login_at", nullable = false)
    private LocalDateTime lastLoginAt;

    @Setter
    @NotNull    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Setter
    @Column(name = "provider_id")
    private String providerId;

    @Setter
    private String provider;

    @Setter
    @NotNull    @Column(nullable = false, length = 20)
    private String role;

    @Setter
    @NotNull    @Column(name = "current_point", nullable = false)
    private long currentPoint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;

}