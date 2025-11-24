package com.nhnacademy.member_server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    @NotNull
    @Column(nullable = false, length = 50)
    private String name;

    @Setter
    @NotNull
    @Column(nullable = false)
    private String password;

    @Setter
    @NotNull
    @Column(nullable = false, length = 20)
    private String phone;

    @Setter
    @NotNull
    @Column(nullable = false, length = 100)
    private String email;

    @Setter
    @NotNull
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Setter
    @NotNull
    @Column(name = "last_login_at", nullable = false)
    private LocalDateTime lastLoginAt;

    @Setter
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Setter
    @Column(name = "provider_id")
    private String providerId;

    @Setter
    private String provider;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Setter
    @NotNull
    @Column(name = "current_point", nullable = false)
    private long currentPoint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;


}