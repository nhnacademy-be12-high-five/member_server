package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.dto.request.member.LoginRequest;
import com.nhnacademy.member_server.dto.request.member.MemberCreateRequest;
import com.nhnacademy.member_server.dto.response.member.TokenDto;
import com.nhnacademy.member_server.global.jwt.WebUtils;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.service.member.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final MemberRepository memberRepository;
    @Value("${jwt.refresh_expiration_time}")
    private Long refreshExpirationTime;

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<TokenDto> login(@RequestBody LoginRequest loginRequest) {
        TokenDto tokenDto = authService.loginUser(loginRequest.getLoginId(), loginRequest.getPassword());
        return ResponseEntity.ok(tokenDto);
    }

    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@RequestBody MemberCreateRequest memberCreateRequest) {
        authService.signup(memberCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/check-id/{loginId}")
    public ResponseEntity<Boolean> checkId(@PathVariable String loginId) {
        return ResponseEntity.status(201).body(memberRepository.existsByLoginId(loginId));
    }


    @PostMapping("/reissue")
    public ResponseEntity<TokenDto> reissue(
            @RequestHeader("X-Refresh-Token") String refreshToken
    ) {
        TokenDto tokenDto = authService.reissue(refreshToken);
        return ResponseEntity.ok(tokenDto);
    }


    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(name = "X-User-ID") Long memberId,
                                       @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {

        authService.logout(WebUtils.getToken(bearerToken), memberId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login/{provider}")
    public ResponseEntity<TokenDto> loginSocial(
            @PathVariable String provider,
            @RequestParam("code") String code
    ) {
        TokenDto tokenDto = authService.loginSocial(provider, code);

        return ResponseEntity.ok(tokenDto);
    }
}
