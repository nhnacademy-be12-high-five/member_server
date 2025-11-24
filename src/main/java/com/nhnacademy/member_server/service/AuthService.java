package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.dto.request.SignupRequest;
import com.nhnacademy.member_server.dto.response.TokenDto;
import com.nhnacademy.member_server.entity.Grade;
import com.nhnacademy.member_server.entity.Member;
import com.nhnacademy.member_server.entity.Role;
import com.nhnacademy.member_server.entity.Status;
import com.nhnacademy.member_server.global.jwt.JwtUtil;
import com.nhnacademy.member_server.repository.GradeRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.security.UserDetailsImpl;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final GradeRepository gradeRepository;

    @Value("${jwt.refresh_expiration_time}")
    private Long refreshExpirationTime;

    @Transactional
    public TokenDto loginUser(String loginId, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginId, password)
        );

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        Member member = userDetails.getMember();

        member.setLastLoginAt(LocalDateTime.now());

        Long userId = userDetails.getMember().getId();
        Role role = userDetails.getMember().getRole();

        String accessToken = jwtUtil.createAccessToken(userId, role);
        String refreshToken = jwtUtil.createRefreshToken(userId);

        redisTemplate.opsForValue().set(
                "RT:" + userId,
                refreshToken,
                refreshExpirationTime,
                TimeUnit.MILLISECONDS
        );

        return new TokenDto(accessToken, refreshToken);
    }

    @Transactional
    public void signup(SignupRequest request) {
        if (memberRepository.existsByLoginId(request.getLoginId())) {
            throw new RuntimeException("이미 존재하는 아이디입니다.");
        }

        Grade basicGrade = gradeRepository.findByGradeName("GENERAL")
                .orElseThrow(() -> new RuntimeException("기본 등급이 DB에 없습니다."));

        Member member = Member.builder()
                .loginId(request.getLoginId())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .birthDate(request.getBirthDate())
                .lastLoginAt(LocalDateTime.now())
                .status(Status.ACTIVE)
                .role(Role.USER)
                .currentPoint(0L)
                .grade(basicGrade)
                .build();

        memberRepository.save(member);
    }

    @Transactional
    public TokenDto reissue(String refreshToken) {

        if (!jwtUtil.validateToken(refreshToken)) {
            throw new RuntimeException("유효하지 않은 Refresh Token입니다.");
        }

        Long userId = jwtUtil.getUserId(refreshToken);
        String redisToken = redisTemplate.opsForValue().get("RT:" + userId);

        if (redisToken == null || !redisToken.equals(refreshToken)) {
            throw new RuntimeException("토큰이 만료되었거나 일치하지 않습니다.");
        }

        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("회원을 찾을 수 없습니다."));

        String newAccessToken = jwtUtil.createAccessToken(member.getId(), member.getRole());
        String newRefreshToken = jwtUtil.createRefreshToken(member.getId());

        redisTemplate.opsForValue().set(
                "RT:" + userId,
                newRefreshToken,
                refreshExpirationTime,
                TimeUnit.MILLISECONDS
        );

        return new TokenDto(newAccessToken, newRefreshToken);
    }

    public void logout(Long userId) {
        redisTemplate.delete("RT:" + userId);
    }



}