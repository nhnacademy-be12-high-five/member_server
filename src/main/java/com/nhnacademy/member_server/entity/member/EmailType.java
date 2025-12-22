package com.nhnacademy.member_server.entity.member;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmailType {
    SIGNUP("EMAIL_CHECK:", true),
    
    RESET_PASSWORD("PW_EMAIL_CHECK:", false),

    FIND_ID("ID_EMAIL_CHECK:", false);

    private final String prefix;
    private final boolean checkDuplication;
}