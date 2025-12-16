package com.nhnacademy.member_server.dto.request.member;

import com.nhnacademy.member_server.entity.member.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.LocalDate;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Setter
public class MemberUpdateRequest {

    private String name;

    @Email
    private String email;

    @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "올바른 휴대폰 번호 형식이 아닙니다.")
    private String phone;

    private Gender gender;

    private LocalDate birthDate;
}
