package com.nhnacademy.member_server.service.impl.member;

import com.nhnacademy.member_server.dto.message.CouponIssueMessage;
import com.nhnacademy.member_server.dto.request.member.MemberCreateRequest;
import com.nhnacademy.member_server.dto.response.member.TokenDto;
import com.nhnacademy.member_server.dto.response.social.OAuth2UserInfo;
import com.nhnacademy.member_server.entity.member.*;
import com.nhnacademy.member_server.global.jwt.JwtUtil;
import com.nhnacademy.member_server.repository.GradeRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.security.UserDetailsImpl;
import com.nhnacademy.member_server.service.member.AuthService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.nhnacademy.member_server.service.social.SocialLoginFactory;
import com.nhnacademy.member_server.service.social.SocialLoginStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    private final RabbitTemplate rabbitTemplate;
    private final SocialLoginFactory socialLoginFactory;

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

        String accessToken = jwtUtil.createAccessToken(memberId, role);
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

        Member savedMember = memberRepository.save(member);

        try {
            CouponIssueMessage message = new CouponIssueMessage(savedMember.getId());
            rabbitTemplate.convertAndSend("coupon-welcome-queue", message);
            log.info("신규 회원({}) 웰컴 쿠폰 지급 메시지 발행 완료", savedMember.getId());
        }catch (Exception e){
            log.error("웰컴 쿠폰 메시지 발행 실패: {}", e.getMessage());
        }

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

        String newAccessToken = jwtUtil.createAccessToken(member.getId(), member.getRole());
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

    @Override
    @Transactional
    public TokenDto loginSocial(String provider, String code) {
        SocialLoginStrategy strategy = socialLoginFactory.getStrategy(provider);
        OAuth2UserInfo userInfo = strategy.getUserInfo(code);
        String providerId = userInfo.getProviderId();

        Member member = memberRepository.findByProviderId(providerId).orElseGet(() -> {
            log.info("소셜 신규 회원 감지. 자동 가입 진행: {} / {}", provider, userInfo.getName());


            return socialSignup(userInfo);
        });

        log.info(">>> DB 저장 성공! Member ID: {}, Role: {}", member.getId(), member.getRole());

        String accessToken = jwtUtil.createAccessToken(member.getId(), member.getRole());
        log.info(">>> Access Token 발급 성공");

        String refreshToken = jwtUtil.createRefreshToken(member.getId());
        log.info(">>> Refresh Token 발급 성공");

        redisTemplate.opsForValue().set(
                "RT:" + member.getId(),
                refreshToken,
                refreshExpirationTime,
                TimeUnit.MILLISECONDS
        );

        return new TokenDto(accessToken, refreshToken);
    }

    private Member socialSignup(OAuth2UserInfo userInfo) {
        String provider = userInfo.getProvider();
        String providerId = userInfo.getProviderId();

        String randomPassword = passwordEncoder.encode(UUID.randomUUID().toString());

        String uniqueLoginId = provider + "_" + providerId;

        Grade basicGrade = gradeRepository.findByGradeName("GENERAL")
                .orElseGet(() -> gradeRepository.save(
                        Grade.builder().gradeName("GENERAL").min(0).pointRate(new BigDecimal("0.01")).build()
                ));

        String realName = (userInfo.getName() != null) ? userInfo.getName() : provider + " User";
        String realEmail = (userInfo.getEmail() != null) ? userInfo.getEmail() : uniqueLoginId + "@no-email.com";
        String realPhone = (userInfo.getMobile() != null) ? userInfo.getMobile() : "010-0000-0000";

        if (realPhone.startsWith("82")) {
            realPhone = "010" + realPhone.substring(4);
        }

        Gender gender = Gender.UNKNOWN;
        if ("MALE".equals(userInfo.getGender())) gender = Gender.MALE;
        else if ("FEMALE".equals(userInfo.getGender())) gender = Gender.FEMALE;

        log.info(">>> 생일 : {}", userInfo.getBirthday());

        java.time.LocalDate birthDate = java.time.LocalDate.now();
        String rawBirth = userInfo.getBirthday();
        if (rawBirth != null) {
            try {
                if (rawBirth.length() == 8) {
                    birthDate = java.time.LocalDate.parse(rawBirth, java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
                }
                else if (rawBirth.length() == 4) {
                    String fullBirth = "0000" + rawBirth;
                    birthDate = java.time.LocalDate.parse(fullBirth, java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
                }
            } catch (Exception e) {
                log.warn("생일 파싱 실패 (기본값 사용): {}", rawBirth);
            }
        }

        Member member = Member.builder()
                .loginId(uniqueLoginId)
                .password(randomPassword)
                .name(realName)
                .email(realEmail)
                .phone(realPhone)
                .birthDate(birthDate)
                .gender(gender)
                .status(Status.ACTIVE)
                .role(Role.USER)
                .currentPoint(0L)
                .grade(basicGrade)
                .lastLoginAt(java.time.LocalDateTime.now())
                .provider(provider)
                .providerId(providerId)
                .build();

        Member savedMember = memberRepository.save(member);

        try {
            CouponIssueMessage message = new CouponIssueMessage(savedMember.getId());
            rabbitTemplate.convertAndSend("coupon-welcome-queue", message);
            log.info("신규 회원({}) 웰컴 쿠폰 지급 메시지 발행 완료", savedMember.getId());
        }catch (Exception e){
            log.error("웰컴 쿠폰 메시지 발행 실패: {}", e.getMessage());
        }

        return savedMember;

    }

}