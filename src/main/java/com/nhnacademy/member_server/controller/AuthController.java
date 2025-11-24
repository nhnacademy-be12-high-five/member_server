package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.dto.request.LoginRequest;
import com.nhnacademy.member_server.dto.request.SignupRequest;
import com.nhnacademy.member_server.dto.response.LoginResponse;
import com.nhnacademy.member_server.dto.response.TokenDto;
import com.nhnacademy.member_server.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    @Value("${jwt.refresh_expiration_time}")
    private Long refreshExpirationTime;

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        TokenDto tokenDto = authService.loginUser(loginRequest.getLoginId(), loginRequest.getPassword());
        ResponseCookie refreshCookie = ResponseCookie.from("refresh-token", tokenDto.getRefreshToken())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(refreshExpirationTime)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new LoginResponse(tokenDto.getAccessToken()));
    }

    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@RequestBody SignupRequest signupRequest) {
        authService.signup(signupRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // [추가] 토큰 재발급 엔드포인트
    @PostMapping("/reissue")
    public ResponseEntity<LoginResponse> reissue(
            @CookieValue(name = "refresh-token", required = false) String refreshToken
    ) {
        if (refreshToken == null) {
            throw new RuntimeException("Refresh Token 쿠키가 없습니다.");
        }

        TokenDto tokenDto = authService.reissue(refreshToken);

        ResponseCookie refreshCookie = ResponseCookie.from("refresh-token", tokenDto.getRefreshToken())
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(refreshExpirationTime)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new LoginResponse(tokenDto.getAccessToken()));
    }


    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(name = "X-USER-ID") long loginId) {
        authService.logout(loginId);
        ResponseCookie deleteCookie = ResponseCookie.from("refresh-token", "")
                .path("/")
                .httpOnly(true)
                .secure(false)
                .maxAge(0)
                .build();

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .build();
    }


}
