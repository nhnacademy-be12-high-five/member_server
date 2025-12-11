package com.nhnacademy.member_server.dto.request.member;

import com.nhnacademy.member_server.entity.member.Gender;
import com.nhnacademy.member_server.entity.member.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@AllArgsConstructor
@RequiredArgsConstructor
public class MemberCreateRequest {
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

    @NotNull(message = "성별은 필수입니다.")
    private Gender gender;

    private LocalDate birthDate;


    private Role role;
}
