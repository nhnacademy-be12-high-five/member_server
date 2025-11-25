package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.dto.request.LoginRequest;
import com.nhnacademy.member_server.dto.request.MemberCreateRequest;
import com.nhnacademy.member_server.dto.response.LoginResponse;
import com.nhnacademy.member_server.dto.response.TokenDto;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.service.impl.AuthServiceImpl;
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

    private final MemberRepository memberRepository;
    @Value("${jwt.refresh_expiration_time}")
    private Long refreshExpirationTime;

    private final AuthServiceImpl authServiceImpl;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        TokenDto tokenDto = authServiceImpl.loginUser(loginRequest.getLoginId(), loginRequest.getPassword());
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
    public ResponseEntity<Void> signup(@RequestBody MemberCreateRequest memberCreateRequest) {
        authServiceImpl.signup(memberCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/check-id/{loginId}")
    public ResponseEntity<Boolean> checkId(@PathVariable String loginId) {
        return ResponseEntity.status(201).body(memberRepository.existsByLoginId(loginId));
    }


    @PostMapping("/reissue")
    public ResponseEntity<LoginResponse> reissue(
            @CookieValue(name = "refresh-token", required = false) String refreshToken
    ) {
        if (refreshToken == null) {
            throw new RuntimeException("Refresh Token 쿠키가 없습니다.");
        }

        TokenDto tokenDto = authServiceImpl.reissue(refreshToken);

        ResponseCookie refreshCookie = ResponseCookie.from("refresh-token", tokenDto.getRefreshToken())
                .httpOnly(true)
                .secure(false)
                //secure 부분은 배포상태에서 https 사용하면 true로 변경해주기
                .path("/")
                .maxAge(refreshExpirationTime)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new LoginResponse(tokenDto.getAccessToken()));
    }


    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(name = "X-USER-ID") long loginId) {
        authServiceImpl.logout(loginId);
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
