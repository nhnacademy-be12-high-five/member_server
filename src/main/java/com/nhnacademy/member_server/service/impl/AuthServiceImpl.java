package com.nhnacademy.member_server.service.impl;

import com.nhnacademy.member_server.dto.request.MemberCreateRequest;
import com.nhnacademy.member_server.dto.response.TokenDto;
import com.nhnacademy.member_server.entity.Grade;
import com.nhnacademy.member_server.entity.Member;
import com.nhnacademy.member_server.entity.Role;
import com.nhnacademy.member_server.entity.Status;
import com.nhnacademy.member_server.global.jwt.JwtUtil;
import com.nhnacademy.member_server.repository.GradeRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.security.UserDetailsImpl;
import com.nhnacademy.member_server.service.AuthService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final GradeRepository gradeRepository;

    @Value("${jwt.refresh_expiration_time}")
    private Long refreshExpirationTime;

    @Override
    @Transactional
    public TokenDto loginUser(String loginId, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginId, password)
        );

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Member inputMember =  userDetails.getMember();
        Member dbMember = memberRepository.findById(inputMember.getId()).orElseThrow(() -> new RuntimeException("존재하지 않는 회원"));

        dbMember.setLastLoginAt(LocalDateTime.now());

        Long memberId = userDetails.getMember().getId();
        Role role = userDetails.getMember().getRole();

        String accessToken = jwtUtil.createAccessToken(memberId, role, loginId);
        String refreshToken = jwtUtil.createRefreshToken(memberId);

        redisTemplate.opsForValue().set(
                "RT:" + memberId,
                refreshToken,
                refreshExpirationTime,
                TimeUnit.MILLISECONDS
        );

        return new TokenDto(accessToken, refreshToken);
    }

    @Override
    @Transactional
    public void signup(MemberCreateRequest request) {
        if (memberRepository.existsByLoginId(request.getLoginId())) {
            throw new RuntimeException("이미 존재하는 아이디입니다.");
        }
        else if (memberRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("이미 존재하는 이메일입니다.");
        } else if (memberRepository.existsByPhone(request.getPhone())) {
            throw new RuntimeException("이미 존재하는 번호입니다.");
        }

        Grade basicGrade = gradeRepository.findByGradeName("GENERAL")
                .orElseGet(() -> gradeRepository.save(Grade.builder()
                        .gradeName("GENERAL")
                        .min(0)
                        .pointRate(new BigDecimal("0.01"))
                        .max(null)
                        .build()
                ));

        Role finalRole = Role.USER;

        Member member = Member.builder()
                .loginId(request.getLoginId())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .gender(request.getGender())
                .phone(request.getPhone())
                .email(request.getEmail())
                .birthDate(request.getBirthDate())
                .lastLoginAt(LocalDateTime.now())
                .status(Status.ACTIVE)
                .role(finalRole)
                .currentPoint(0L)
                .grade(basicGrade)
                .build();

        memberRepository.save(member);
    }

    @Override
    @Transactional
    public TokenDto reissue(String refreshToken) {

        if (!jwtUtil.validateToken(refreshToken)) {
            throw new RuntimeException("유효하지 않은 Refresh Token입니다.");
        }

        Long memberId = jwtUtil.getUserId(refreshToken);
        String redisToken = redisTemplate.opsForValue().get("RT:" + memberId);

        if (redisToken == null || !redisToken.equals(refreshToken)) {
            throw new RuntimeException("토큰이 만료되었거나 일치하지 않습니다.");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("회원을 찾을 수 없습니다."));

        if (member.getStatus().equals(Status.WITHDRAWAL)) {
            throw new RuntimeException("탈퇴된 회원입니다.");
        }

        String newAccessToken = jwtUtil.createAccessToken(member.getId(), member.getRole(), member.getLoginId());
        String newRefreshToken = jwtUtil.createRefreshToken(member.getId());

        redisTemplate.opsForValue().set(
                "RT:" + memberId,
                newRefreshToken,
                refreshExpirationTime,
                TimeUnit.MILLISECONDS
        );

        return new TokenDto(newAccessToken, newRefreshToken);
    }

    @Override
    public void logout(String accessToken, Long memberId) {
        redisTemplate.delete("RT:" + memberId);
        long expiration = jwtUtil.getRemainingTime(accessToken);
        if (expiration > 0) {
            redisTemplate.opsForValue().set(accessToken, "logout", expiration, TimeUnit.MILLISECONDS);
        }
    }



}