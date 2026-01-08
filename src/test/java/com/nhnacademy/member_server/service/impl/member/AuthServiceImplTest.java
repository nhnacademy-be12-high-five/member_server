package com.nhnacademy.member_server.service.impl.member;

import com.nhnacademy.member_server.dto.event.MemberLoginEvent;
import com.nhnacademy.member_server.dto.event.MemberLogoutEvent;
import com.nhnacademy.member_server.dto.message.CouponIssueMessage;
import com.nhnacademy.member_server.dto.request.member.MemberCreateRequest;
import com.nhnacademy.member_server.dto.request.member.PasswordResetRequest;
import com.nhnacademy.member_server.dto.response.member.TokenDto;
import com.nhnacademy.member_server.dto.response.social.OAuth2UserInfo;
import com.nhnacademy.member_server.entity.member.*;
import com.nhnacademy.member_server.exception.BusinessException;
import com.nhnacademy.member_server.exception.ErrorCode;
import com.nhnacademy.member_server.global.jwt.JwtUtil;
import com.nhnacademy.member_server.repository.GradeRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.security.UserDetailsImpl;
import com.nhnacademy.member_server.service.member.EmailService;
import com.nhnacademy.member_server.service.social.SocialLoginFactory;
import com.nhnacademy.member_server.service.social.SocialLoginStrategy;
import com.nhnacademy.member_server.utils.Sha256Utils;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @InjectMocks
    private AuthServiceImpl authService;

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private MemberRepository memberRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private GradeRepository gradeRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private SocialLoginFactory socialLoginFactory;
    @Mock private EmailService emailService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private Sha256Utils sha256Utils;

    private Member member;
    private Grade grade;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ReflectionTestUtils.setField(authService, "refreshExpirationTime", 3600000L);

        grade = Grade.builder().gradeName("GENERAL").pointRate(BigDecimal.valueOf(0.01)).build();
        member = Member.builder()
                .id(1L)
                .loginId("testUser")
                .password("encodedPassword")
                .email("test@test.com")
                .status(Status.ACTIVE)
                .role(Role.USER)
                .grade(grade)
                .isProfileComplete(true)
                .build();
    }

    @Test
    @DisplayName("로그인 성공")
    void loginUser_Success() {
        String rawPassword = "password123!";
        Authentication authentication = mock(Authentication.class);
        UserDetailsImpl userDetails = new UserDetailsImpl(member);

        when(memberRepository.findByLoginId(member.getLoginId())).thenReturn(Optional.of(member));
        when(passwordEncoder.matches(rawPassword, member.getPassword())).thenReturn(true);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(jwtUtil.createAccessToken(anyLong(), any())).thenReturn("access-token");
        when(jwtUtil.createRefreshToken(anyLong())).thenReturn("refresh-token");

        TokenDto result = authService.loginUser(member.getLoginId(), rawPassword);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        verify(eventPublisher).publishEvent(any(MemberLoginEvent.class));
    }

    @Test
    @DisplayName("로그인 실패 - 탈퇴한 회원")
    void loginUser_Withdrawn() {
        member.setStatus(Status.WITHDRAWAL);
        when(memberRepository.findByLoginId(member.getLoginId())).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.loginUser(member.getLoginId(), "anyPw"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_WITHDRAWN);
    }

    @Test
    @DisplayName("회원가입 성공 - RabbitMQ 오류가 발생해도 가입은 성공해야 함")
    void signup_Success_EvenIfRabbitMQFails() {
        MemberCreateRequest request = MemberCreateRequest.builder().loginId("newUser").password("pw").name("nm").phone("010-0000-0000").email("e@e.com").birthDate(LocalDate.now()).gender(Gender.MALE).build();

        when(memberRepository.existsByLoginId(any())).thenReturn(false);
        when(memberRepository.existsByEmailHash(any())).thenReturn(false);
        when(memberRepository.existsByPhoneHash(any())).thenReturn(false);
        when(gradeRepository.findByGradeName("GENERAL")).thenReturn(Optional.of(grade));
        when(memberRepository.save(any(Member.class))).thenReturn(member);
        doThrow(new RuntimeException("MQ Error")).when(rabbitTemplate).convertAndSend(anyString(), any(CouponIssueMessage.class));

        authService.signup(request);

        verify(memberRepository).save(any(Member.class));
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void signup_DuplicateEmail() {
        MemberCreateRequest request = MemberCreateRequest.builder().loginId("new").email("dup@e.com").name("n").phone("010").build();
        when(memberRepository.existsByLoginId(any())).thenReturn(false);
        when(memberRepository.existsByEmailHash(any())).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    @DisplayName("회원가입 실패 - 전화번호 중복")
    void signup_DuplicatePhone() {
        MemberCreateRequest request = MemberCreateRequest.builder().loginId("new").email("e@e.com").name("n").phone("010-1111-2222").build();
        when(memberRepository.existsByLoginId(any())).thenReturn(false);
        when(memberRepository.existsByEmailHash(any())).thenReturn(false);
        when(memberRepository.existsByPhoneHash(any())).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_PHONE);
    }


    @Test
    @DisplayName("토큰 재발급 실패 - JWT 유효성 검증 실패")
    void reissue_InvalidJwt() {
        when(jwtUtil.validateToken("invalid-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.reissue("invalid-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 유저가 탈퇴 상태")
    void reissue_MemberWithdrawn() {
        String refreshToken = "valid";
        when(jwtUtil.validateToken(refreshToken)).thenReturn(true);
        when(jwtUtil.getUserId(refreshToken)).thenReturn(1L);
        when(valueOperations.get("RT:1")).thenReturn(refreshToken);

        member.setStatus(Status.WITHDRAWAL);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.reissue(refreshToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("소셜 로그인 - 기존 회원 로그인")
    void loginSocial_ExistingUser() {
        String provider = "PAYCO";
        OAuth2UserInfo userInfo = OAuth2UserInfo.builder().provider(provider).providerId("123").build();
        SocialLoginStrategy strategy = mock(SocialLoginStrategy.class);

        when(socialLoginFactory.getStrategy(provider)).thenReturn(strategy);
        when(strategy.getUserInfo(anyString())).thenReturn(userInfo);
        when(memberRepository.findByProviderId("123")).thenReturn(Optional.of(member));
        when(jwtUtil.createAccessToken(any(), any())).thenReturn("access");

        TokenDto result = authService.loginSocial(provider, "code");

        assertThat(result.getAccessToken()).isEqualTo("access");
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("소셜 로그인 - 기존 회원이지만 휴면 상태")
    void loginSocial_Dormant() {
        String provider = "PAYCO";
        OAuth2UserInfo userInfo = OAuth2UserInfo.builder().providerId("123").build();
        SocialLoginStrategy strategy = mock(SocialLoginStrategy.class);
        member.setStatus(Status.DORMANT);

        when(socialLoginFactory.getStrategy(provider)).thenReturn(strategy);
        when(strategy.getUserInfo(any())).thenReturn(userInfo);
        when(memberRepository.findByProviderId("123")).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.loginSocial(provider, "code"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_DORMANT);
    }

    @Test
    @DisplayName("소셜 회원가입 - 생일 파싱 로직 테스트 (8자리, 4자리, 실패)")
    void socialSignup_BirthdayParsing() {
        String provider = "PAYCO";
        SocialLoginStrategy strategy = mock(SocialLoginStrategy.class);
        when(socialLoginFactory.getStrategy(provider)).thenReturn(strategy);
        when(memberRepository.findByProviderId(any())).thenReturn(Optional.empty());
        when(gradeRepository.findByGradeName("GENERAL")).thenReturn(Optional.of(grade));
        when(jwtUtil.createAccessToken(any(), any())).thenReturn("token");

        when(strategy.getUserInfo("code1")).thenReturn(OAuth2UserInfo.builder().providerId("1").birthday("20000101").build());
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));
        TokenDto t1 = authService.loginSocial(provider, "code1");

        when(strategy.getUserInfo("code2")).thenReturn(OAuth2UserInfo.builder().providerId("2").birthday("0505").build());
        authService.loginSocial(provider, "code2");

        when(strategy.getUserInfo("code3")).thenReturn(OAuth2UserInfo.builder().providerId("3").birthday("invalid").build());
        authService.loginSocial(provider, "code3");

        verify(memberRepository, times(3)).save(any(Member.class));
    }


    @Test
    @DisplayName("아이디 찾기 - 인증 실패")
    void findLoginIdByEmail_AuthFail() {
        when(emailService.verifyCode(any(), any(), eq(EmailType.FIND_ID))).thenReturn(false);

        assertThatThrownBy(() -> authService.findLoginIdByEmail("e@e.com", "123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_CODE_MISMATCH);
    }

    @Test
    @DisplayName("아이디 찾기 - 성공 및 마스킹 검증")
    void findLoginIdByEmail_Success() {
        when(emailService.verifyCode(any(), any(), eq(EmailType.FIND_ID))).thenReturn(true);
        when(sha256Utils.encrypt(anyString())).thenReturn("hash");
        when(memberRepository.findByEmailHash("hash")).thenReturn(Optional.of(member));

        String result = authService.findLoginIdByEmail("test@test.com", "123456");

        assertThat(result).isEqualTo("te******");
    }

    @Test
    @DisplayName("아이디 찾기 - 짧은 아이디 마스킹 (2글자)")
    void findLoginIdByEmail_ShortId() {
        Member shortMember = Member.builder().loginId("ab").email("a@a.com").build();

        when(emailService.verifyCode(any(), any(), any())).thenReturn(true);
        when(sha256Utils.encrypt(anyString())).thenReturn("hash");
        when(memberRepository.findByEmailHash("hash")).thenReturn(Optional.of(shortMember));

        String result = authService.findLoginIdByEmail("a@a.com", "123");
        assertThat(result).isEqualTo("a*");
    }

    @Test
    @DisplayName("비밀번호 재설정 - 요청한 LoginId와 이메일 소유주 불일치")
    void resetPassword_IdMismatch() {
        PasswordResetRequest request = new PasswordResetRequest("wrongId", "test@test.com", "123", "pw");

        when(emailService.verifyCode(any(), any(), any())).thenReturn(true);
        when(sha256Utils.encrypt(anyString())).thenReturn("hash");
        when(memberRepository.findByEmailHash("hash")).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }
}