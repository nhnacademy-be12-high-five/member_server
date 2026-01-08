package com.nhnacademy.member_server.service.impl.member;

import com.nhnacademy.member_server.dto.event.MemberLoginEvent;
import com.nhnacademy.member_server.dto.event.MemberLogoutEvent;
import com.nhnacademy.member_server.dto.request.member.MemberCreateRequest;
import com.nhnacademy.member_server.dto.request.member.PasswordResetRequest;
import com.nhnacademy.member_server.dto.response.member.TokenDto;
import com.nhnacademy.member_server.entity.member.*;
import com.nhnacademy.member_server.exception.BusinessException;
import com.nhnacademy.member_server.exception.ErrorCode;
import com.nhnacademy.member_server.global.jwt.JwtUtil;
import com.nhnacademy.member_server.repository.GradeRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.security.UserDetailsImpl;
import com.nhnacademy.member_server.service.member.EmailService;
import com.nhnacademy.member_server.utils.Sha256Utils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @InjectMocks
    AuthServiceImpl authService;

    @Mock
    AuthenticationManager authenticationManager;
    @Mock
    JwtUtil jwtUtil;
    @Mock
    StringRedisTemplate redisTemplate;
    @Mock
    MemberRepository memberRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    GradeRepository gradeRepository;
    @Mock
    RabbitTemplate rabbitTemplate;
    @Mock
    EmailService emailService;
    @Mock
    ValueOperations<String, String> valueOperations;
    @Mock
    ApplicationEventPublisher eventPublisher;
    @Mock
    Sha256Utils sha256Utils;

    @Test
    @DisplayName("로그인 성공 테스트")
    void loginUserSuccessTest() {
        String loginId = "user";
        String password = "pw";
        Member member = Member.builder()
                .id(1L)
                .loginId(loginId)
                .password("encodedPw")
                .status(Status.ACTIVE)
                .role(Role.USER)
                .build();

        given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));
        given(passwordEncoder.matches(password, "encodedPw")).willReturn(true);

        Authentication authentication = mock(Authentication.class);
        UserDetailsImpl userDetails = new UserDetailsImpl(member);
        given(authentication.getPrincipal()).willReturn(userDetails);
        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(authentication);

        given(jwtUtil.createAccessToken(1L, Role.USER)).willReturn("access");
        given(jwtUtil.createRefreshToken(1L)).willReturn("refresh");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        ReflectionTestUtils.setField(authService, "refreshExpirationTime", 1000L);

        TokenDto result = authService.loginUser(loginId, password);

        assertThat(result.getAccessToken()).isEqualTo("access");

        verify(valueOperations).set(any(), any(), any(Long.class), any(TimeUnit.class));
        verify(eventPublisher).publishEvent(any(MemberLoginEvent.class));

    }

    @Test
    @DisplayName("로그인 실패 - 탈퇴한 회원")
    void loginUserWithdrawalTest() {
        String loginId = "user";
        String password = "pw";
        Member member = Member.builder().loginId(loginId).status(Status.WITHDRAWAL).build();

        given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.loginUser(loginId, password))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_WITHDRAWN);
    }

    @Test
    @DisplayName("회원가입 성공 테스트")
    void signupSuccessTest() {
        MemberCreateRequest request = MemberCreateRequest.builder()
                .loginId("new")
                .password("pw")
                .name("name")
                .email("e@e.com")
                .phone("01012345678")
                .build();

        String emailHash = "emailHash";
        String phoneHash = "phoneHash";

        given(sha256Utils.encrypt(request.getEmail())).willReturn(emailHash);
        given(sha256Utils.encrypt("01012345678")).willReturn(phoneHash);

        given(memberRepository.existsByLoginId("new")).willReturn(false);
        given(memberRepository.existsByEmailHash(emailHash)).willReturn(false);
        given(memberRepository.existsByPhoneHash(phoneHash)).willReturn(false);

        given(gradeRepository.findByGradeName("GENERAL")).willReturn(
                Optional.of(Grade.builder().gradeName("GENERAL").pointRate(BigDecimal.ONE).build())
        );
        given(passwordEncoder.encode("pw")).willReturn("encoded");
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> {
            Member m = inv.getArgument(0);
            return Member.builder().id(1L).loginId(m.getLoginId()).build();
        });

        authService.signup(request);

        verify(memberRepository).save(any(Member.class));
        verify(rabbitTemplate).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("로그아웃 테스트")
    void logoutTest() {
        String token = "access";
        Long memberId = 1L;

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.delete("RT:" + memberId)).willReturn(true);
        given(jwtUtil.getRemainingTime(token)).willReturn(1000L);

        authService.logout(token, memberId);

        verify(redisTemplate).delete("RT:" + memberId);
        verify(valueOperations).set(eq(token), eq("logout"), any(Long.class), any(TimeUnit.class));
        verify(eventPublisher).publishEvent(any(MemberLogoutEvent.class));
    }

    @Test
    @DisplayName("토큰 재발급 테스트")
    void reissueTest() {
        String refreshToken = "refresh";
        Long memberId = 1L;

        given(jwtUtil.validateToken(refreshToken)).willReturn(true);
        given(jwtUtil.getUserId(refreshToken)).willReturn(memberId);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("RT:" + memberId)).willReturn(refreshToken);

        Member member = Member.builder().id(memberId).role(Role.USER).status(Status.ACTIVE).build();
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        given(jwtUtil.createAccessToken(memberId, Role.USER)).willReturn("newAccess");
        given(jwtUtil.createRefreshToken(memberId)).willReturn("newRefresh");

        ReflectionTestUtils.setField(authService, "refreshExpirationTime", 1000L);

        TokenDto result = authService.reissue(refreshToken);

        assertThat(result.getAccessToken()).isEqualTo("newAccess");
    }

    @Test
    @DisplayName("아이디 찾기 검증 및 조회 테스트")
    void findLoginIdByEmailTest() {
        String email = "test@test.com";
        String hash = "hashedEmail";
        String code = "123456";
        Member member = Member.builder().loginId("tester").build();

        given(emailService.verifyCode(email, code, EmailType.FIND_ID)).willReturn(true);

        given(sha256Utils.encrypt(email)).willReturn(hash);
        given(memberRepository.findByEmailHash(hash)).willReturn(Optional.of(member));

        String result = authService.findLoginIdByEmail(email, code);

        assertThat(result).isEqualTo("te****");
    }

    @Test
    @DisplayName("비밀번호 재설정 테스트")
    void resetPasswordTest() {
        String loginId = "test";
        String email = "e@e.com";
        String hash = "hashedEmail";
        String authCode = "code";
        String newPassword = "newPw";

        PasswordResetRequest request = new PasswordResetRequest();
        ReflectionTestUtils.setField(request, "loginId", loginId);
        ReflectionTestUtils.setField(request, "newPassword", newPassword);
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "authCode", authCode);

        Member member = Member.builder()
                .loginId(loginId)
                .email(email)
                .status(Status.ACTIVE)
                .build();

        given(emailService.verifyCode(email, authCode, EmailType.RESET_PASSWORD)).willReturn(true);

        given(sha256Utils.encrypt(email)).willReturn(hash);
        given(memberRepository.findByEmailHash(hash)).willReturn(Optional.of(member));

        given(passwordEncoder.encode(newPassword)).willReturn("encoded");

        authService.resetPassword(request);

        assertThat(member.getPassword()).isEqualTo("encoded");
        verify(memberRepository).save(member);
    }
}