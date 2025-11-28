package com.nhnacademy.member_server.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MemberPrincipal implements Serializable {
    private Long memberId;
    private String loginId;
    private String role;
}