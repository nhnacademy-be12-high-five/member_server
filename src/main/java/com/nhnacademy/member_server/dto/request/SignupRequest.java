package com.nhnacademy.member_server.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class SignupRequest {
    @NotBlank
    String loginId;

    @NotBlank
    String password;

    @NotBlank
    String name;

    @NotBlank
    String phone;

    @Email
    private String email;

    private LocalDate birthDate;
}
