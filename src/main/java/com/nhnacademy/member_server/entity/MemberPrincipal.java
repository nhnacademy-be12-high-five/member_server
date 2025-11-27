package com.nhnacademy.member_server.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

@Getter
@AllArgsConstructor
public class MemberPrincipal implements Serializable {
    private Long memberId;
    private String loginId;
    private String role;
}