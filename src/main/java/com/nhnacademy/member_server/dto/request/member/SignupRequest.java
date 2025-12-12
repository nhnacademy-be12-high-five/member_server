package com.nhnacademy.member_server.dto.request.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;

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
