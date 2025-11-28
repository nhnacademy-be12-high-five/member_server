package com.nhnacademy.member_server.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nhnacademy.member_server.entity.Member;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class MemberResponse {
    String name;
    String email;
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate birthDate;
    String phone;
    String status;
    String gradeName;

    public static MemberResponse from(Member member) {
        return MemberResponse.builder()
                .name(member.getName())
                .email(member.getEmail())
                .phone(member.getPhone())
                .birthDate(member.getBirthDate())
                .status(member.getStatus().name())
                .gradeName(member.getGrade().getGradeName())
                .build();
    }
}
