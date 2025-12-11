package com.nhnacademy.member_server.service.impl.member;

import com.nhnacademy.member_server.dto.message.CouponIssueMessage;
import com.nhnacademy.member_server.dto.request.member.MemberCreateRequest;
import com.nhnacademy.member_server.dto.response.member.TokenDto;
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
//    private final SocialLoginFactory socialLoginFactory;

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

//    @Override
//    public TokenDto loginSocial(String provider, String code) {
//        SocialLoginStrategy strategy = socialLoginFactory.getStrategy(provider);
//
//        String providerId = strategy.getOAuth2MemberId(code);
//
//        Member member = memberRepository.findByProviderId(providerId).orElseGet(() -> {
//            log.info("소셜 신규 회원. 자동 가입 {} / {}", provider, providerId);
//            return socialSignup(provider, providerId);
//        });
//
//        String accessToken = jwtUtil.createAccessToken(member.getId(), member.getRole());
//        String refreshToken = jwtUtil.createRefreshToken(member.getId());
//
//        // 6. [Redis 저장] Refresh Token 저장
//        redisTemplate.opsForValue().set(
//                "RT:" + member.getId(),
//                refreshToken,
//                refreshExpirationTime,
//                TimeUnit.MILLISECONDS
//        );
//
//        // 7. 결과 반환
//        return new TokenDto(accessToken, refreshToken);
//
//    }
//
//    private Member socialSignup(String provider, String providerId) {
//        // 1. 비밀번호: 소셜 회원은 비번을 안 쓰지만, DB NotNull 제약 때문에 랜덤값 생성
//        String randomPassword = passwordEncoder.encode(UUID.randomUUID().toString());
//
//        // 2. Login ID: 중복 안 되게 조합 (예: PAYCO_12345...)
//        // (Tip: providerId가 길면 잘라쓰거나 그대로 써도 됨)
//        String uniqueLoginId = provider + "_" + providerId;
//
//        // 3. 기본 등급 가져오기 (기존 코드 재사용)
//        Grade basicGrade = gradeRepository.findByGradeName("GENERAL")
//                .orElseGet(() -> gradeRepository.save(
//                        Grade.builder().gradeName("GENERAL").min(0).pointRate(new BigDecimal("0.01")).build()
//                ));
//
//        // 4. 회원 엔티티 생성 (빌더)
//        Member member = Member.builder()
//                .loginId(uniqueLoginId)
//                .password(randomPassword)
//                .name(provider + " User") // 닉네임 (추후 마이페이지에서 변경 유도)
//                .email(uniqueLoginId + "@social.tmp") // 이메일 (임시값, 필요시 PAYCO Response에서 꺼내 써도 됨)
//                .phone("010-0000-0000") // 전화번호 (필수라면 임시값)
//                .birthDate(java.time.LocalDate.now()) // 생일 (임시값)
//                .status(Status.ACTIVE)
//                .role(Role.USER)
//                .currentPoint(0L)
//                .grade(basicGrade)
//                .lastLoginAt(java.time.LocalDateTime.now())
//                .provider(provider)      // "PAYCO"
//                .providerId(providerId)  // 식별자
//                .gender(Gender.UNKNOWN)  // (필요시 추가)
//                .build();
//
//        return memberRepository.save(member);
//    }

}