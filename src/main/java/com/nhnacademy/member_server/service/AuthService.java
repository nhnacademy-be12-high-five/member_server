package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.dto.request.MemberCreateRequest;
import com.nhnacademy.member_server.dto.response.TokenDto;

public interface AuthService {
    TokenDto loginUser(String loginId, String password);

    void signup(MemberCreateRequest request);

    TokenDto reissue(String refreshToken);

    void logout(String accessToken, Long userId);
}
