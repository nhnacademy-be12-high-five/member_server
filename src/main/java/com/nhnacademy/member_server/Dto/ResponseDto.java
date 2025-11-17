package com.nhnacademy.member_server.Dto;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class ResponseDto {
    @Id
    private Long id;

    private boolean isMember;
}
