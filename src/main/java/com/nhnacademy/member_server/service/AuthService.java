package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.dto.request.SignupRequest;
import com.nhnacademy.member_server.dto.response.TokenDto;

public interface AuthService {
    TokenDto loginUser(String loginId, String password);

    void signup(SignupRequest request);

    TokenDto reissue(String refreshToken);

    void logout(Long userId);
}
