package com.nhnacademy.member_server.service.impl.member;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

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
    SocialLoginFactory socialLoginFactory;
    @Mock
    SocialLoginStrategy socialLoginStrategy;
    @Mock
    ValueOperations<String, String> valueOperations;

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
                .isProfileComplete(true)
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
    }

    @Test
    @DisplayName("로그인 실패 - 존재하지 않는 회원")
    void loginUser_NotFound_Test() {
        String loginId = "unknown";
        given(memberRepository.findByLoginId(loginId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loginUser(loginId, "pw"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("로그인 실패 - 휴면 회원")
    void loginUser_Dormant_Test() {
        String loginId = "dormant";
        Member member = Member.builder().loginId(loginId).status(Status.DORMANT).build();
        given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.loginUser(loginId, "pw"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_DORMANT);
    }

    @Test
    @DisplayName("로그인 실패 - 탈퇴한 회원")
    void loginUser_Withdrawal_Test() {
        String loginId = "withdrawn";
        Member member = Member.builder().loginId(loginId).status(Status.WITHDRAWAL).build();
        given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.loginUser(loginId, "pw"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_WITHDRAWN);
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치")
    void loginUser_PasswordMismatch_Test() {
        String loginId = "user";
        String wrongPw = "wrong";
        Member member = Member.builder()
                .loginId(loginId)
                .password("encoded")
                .status(Status.ACTIVE)
                .build();

        given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));
        given(passwordEncoder.matches(wrongPw, "encoded")).willReturn(false);

        assertThatThrownBy(() -> authService.loginUser(loginId, wrongPw))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_FAILED);
    }


    @Test
    @DisplayName("회원가입 성공 테스트 (쿠폰 발행 포함)")
    void signupSuccessTest() {
        MemberCreateRequest request = MemberCreateRequest.builder()
                .loginId("new")
                .password("pw")
                .name("name")
                .email("e@e.com")
                .phone("010-1234-5678")
                .birthDate(LocalDate.now())
                .gender(Gender.MALE)
                .build();

        given(memberRepository.existsByLoginId("new")).willReturn(false);
        given(memberRepository.existsByEmail("e@e.com")).willReturn(false);
        given(memberRepository.existsByPhone("01012345678")).willReturn(false);

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
    @DisplayName("회원가입 실패 - 아이디 중복")
    void signup_DuplicateId_Test() {
        MemberCreateRequest request = MemberCreateRequest.builder()
                .loginId("dupId").name("n").email("e").phone("010").build();
        given(memberRepository.existsByLoginId("dupId")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_LOGIN_ID);
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void signup_DuplicateEmail_Test() {
        MemberCreateRequest request = MemberCreateRequest.builder()
                .loginId("id").name("n").email("dup@e.com").phone("010").build();
        given(memberRepository.existsByLoginId("id")).willReturn(false);
        given(memberRepository.existsByEmail("dup@e.com")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    @DisplayName("회원가입 실패 - 전화번호 중복")
    void signup_DuplicatePhone_Test() {
        MemberCreateRequest request = MemberCreateRequest.builder()
                .loginId("id").name("n").email("e@e.com").phone("010-1234-5678").build();
        given(memberRepository.existsByLoginId("id")).willReturn(false);
        given(memberRepository.existsByEmail("e@e.com")).willReturn(false);
        given(memberRepository.existsByPhone("01012345678")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_PHONE);
    }

    @Test
    @DisplayName("회원가입 성공 - RabbitMQ 예외 발생해도 가입은 진행됨")
    void signup_RabbitMQ_Exception_Test() {
        MemberCreateRequest request = MemberCreateRequest.builder()
                .loginId("new").password("pw").name("n").email("e").phone("010").birthDate(LocalDate.now()).gender(Gender.MALE).build();

        given(memberRepository.existsByLoginId(any())).willReturn(false);
        given(gradeRepository.findByGradeName("GENERAL")).willReturn(Optional.of(Grade.builder().build()));
        given(memberRepository.save(any(Member.class))).willReturn(Member.builder().id(1L).build());

        doThrow(new RuntimeException("MQ Error")).when(rabbitTemplate).convertAndSend(anyString(), any(Object.class));

        authService.signup(request);

        verify(memberRepository).save(any(Member.class));
    }


    @Test
    @DisplayName("토큰 재발급 실패 - 유효하지 않은 JWT")
    void reissue_InvalidJwt_Test() {
        given(jwtUtil.validateToken("invalid")).willReturn(false);

        assertThatThrownBy(() -> authService.reissue("invalid"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("토큰 재발급 실패 - Redis 토큰 없음 또는 불일치")
    void reissue_RedisMismatch_Test() {
        String token = "validToken";
        Long memberId = 1L;
        given(jwtUtil.validateToken(token)).willReturn(true);
        given(jwtUtil.getUserId(token)).willReturn(memberId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        given(valueOperations.get("RT:" + memberId)).willReturn("differentToken");

        assertThatThrownBy(() -> authService.reissue(token))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 탈퇴한 회원")
    void reissue_WithdrawnMember_Test() {
        String token = "validToken";
        Long memberId = 1L;
        given(jwtUtil.validateToken(token)).willReturn(true);
        given(jwtUtil.getUserId(token)).willReturn(memberId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("RT:" + memberId)).willReturn(token);

        Member withdrawnMember = Member.builder().id(memberId).status(Status.WITHDRAWAL).build();
        given(memberRepository.findById(memberId)).willReturn(Optional.of(withdrawnMember));

        assertThatThrownBy(() -> authService.reissue(token))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("토큰 재발급 성공")
    void reissueSuccessTest() {
        String refreshToken = "refresh";
        Long memberId = 1L;

        given(jwtUtil.validateToken(refreshToken)).willReturn(true);
        given(jwtUtil.getUserId(refreshToken)).willReturn(memberId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("RT:" + memberId)).willReturn(refreshToken);

        Member member = Member.builder().id(memberId).role(Role.USER).status(Status.ACTIVE).isProfileComplete(true).build();
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        given(jwtUtil.createAccessToken(memberId, Role.USER)).willReturn("newAccess");
        given(jwtUtil.createRefreshToken(memberId)).willReturn("newRefresh");

        ReflectionTestUtils.setField(authService, "refreshExpirationTime", 1000L);

        TokenDto result = authService.reissue(refreshToken);

        assertThat(result.getAccessToken()).isEqualTo("newAccess");
    }


    @Test
    @DisplayName("로그아웃 테스트")
    void logoutTest() {
        String token = "access";
        Long memberId = 1L;

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(jwtUtil.getRemainingTime(token)).willReturn(1000L);

        authService.logout(token, memberId);

        verify(redisTemplate).delete("RT:" + memberId);
        verify(valueOperations).set(eq(token), eq("logout"), any(Long.class), any(TimeUnit.class));
    }


    @Test
    @DisplayName("소셜 로그인 - 기존 회원 로그인 성공")
    void loginSocial_ExistingMember_Success() {
        String provider = "google";
        String code = "authCode";
        String providerId = "12345";

        OAuth2UserInfo userInfo = mock(OAuth2UserInfo.class);
        given(userInfo.getProviderId()).willReturn(providerId);

        given(socialLoginFactory.getStrategy(provider)).willReturn(socialLoginStrategy);
        given(socialLoginStrategy.getUserInfo(code)).willReturn(userInfo);

        Member member = Member.builder()
                .id(1L).status(Status.ACTIVE).role(Role.USER).isProfileComplete(true)
                .build();
        given(memberRepository.findByProviderId(providerId)).willReturn(Optional.of(member));

        given(jwtUtil.createAccessToken(any(), any())).willReturn("access");
        given(jwtUtil.createRefreshToken(any())).willReturn("refresh");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        ReflectionTestUtils.setField(authService, "refreshExpirationTime", 1000L);

        TokenDto result = authService.loginSocial(provider, code);

        assertThat(result.getAccessToken()).isEqualTo("access");
    }

    @Test
    @DisplayName("소셜 로그인 - 신규 회원 자동 가입 (생일 8자리 파싱)")
    void loginSocial_NewMember_Signup_Success() {
        String provider = "naver";
        String code = "authCode";
        OAuth2UserInfo userInfo = mock(OAuth2UserInfo.class);
        given(userInfo.getProvider()).willReturn(provider);
        given(userInfo.getProviderId()).willReturn("pid");
        given(userInfo.getName()).willReturn("name");
        given(userInfo.getEmail()).willReturn("e@e.com");
        given(userInfo.getMobile()).willReturn("010-1234-5678");
        given(userInfo.getGender()).willReturn("MALE");
        given(userInfo.getBirthday()).willReturn("19900101");

        given(socialLoginFactory.getStrategy(provider)).willReturn(socialLoginStrategy);
        given(socialLoginStrategy.getUserInfo(code)).willReturn(userInfo);

        given(memberRepository.findByProviderId("pid")).willReturn(Optional.empty());
        given(gradeRepository.findByGradeName("GENERAL")).willReturn(Optional.of(Grade.builder().build()));

        given(memberRepository.save(any(Member.class))).willAnswer(inv -> {
            Member m = inv.getArgument(0);
            return Member.builder()
                    .id(2L).role(m.getRole()).isProfileComplete(false)
                    .birthDate(m.getBirthDate())
                    .build();
        });

        given(jwtUtil.createAccessToken(any(), any())).willReturn("access");
        given(jwtUtil.createRefreshToken(any())).willReturn("refresh");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        ReflectionTestUtils.setField(authService, "refreshExpirationTime", 1000L);

        authService.loginSocial(provider, code);

        verify(memberRepository).save(any(Member.class));
        verify(rabbitTemplate).convertAndSend(any(String.class), any(CouponIssueMessage.class));
    }

    @Test
    @DisplayName("소셜 로그인 - 휴면 회원 예외")
    void loginSocial_Dormant_Exception() {
        String provider = "google";
        OAuth2UserInfo userInfo = mock(OAuth2UserInfo.class);
        given(userInfo.getProviderId()).willReturn("pid");
        given(socialLoginFactory.getStrategy(provider)).willReturn(socialLoginStrategy);
        given(socialLoginStrategy.getUserInfo(any())).willReturn(userInfo);

        Member member = Member.builder().status(Status.DORMANT).build();
        given(memberRepository.findByProviderId("pid")).willReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.loginSocial(provider, "code"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_DORMANT);
    }


    @Test
    @DisplayName("아이디 찾기 - 인증번호 불일치")
    void findLoginId_AuthCodeMismatch_Test() {
        String email = "e@e.com";
        given(emailService.verifyCode(email, "wrong", EmailType.FIND_ID)).willReturn(false);

        assertThatThrownBy(() -> authService.findLoginIdByEmail(email, "wrong"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_CODE_MISMATCH);
    }

    @Test
    @DisplayName("아이디 찾기 - 마스킹 로직 테스트 (짧은 아이디)")
    void findLoginId_ShortId_Test() {
        String email = "e@e.com";
        String code = "123456";
        Member member = Member.builder().loginId("a").build();

        given(emailService.verifyCode(email, code, EmailType.FIND_ID)).willReturn(true);
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(member));

        String result = authService.findLoginIdByEmail(email, code);

        assertThat(result).isEqualTo("a");
    }

    @Test
    @DisplayName("아이디 찾기 - 마스킹 로직 테스트 (2글자 아이디)")
    void findLoginId_TwoCharId_Test() {
        String email = "e@e.com";
        String code = "123456";
        Member member = Member.builder().loginId("ab").build();

        given(emailService.verifyCode(email, code, EmailType.FIND_ID)).willReturn(true);
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(member));

        String result = authService.findLoginIdByEmail(email, code);

        assertThat(result).isEqualTo("a*");
    }

    @Test
    @DisplayName("비밀번호 재설정 - 아이디 불일치 예외")
    void resetPassword_IdMismatch_Test() {
        PasswordResetRequest request = new PasswordResetRequest();
        ReflectionTestUtils.setField(request, "loginId", "inputID");
        ReflectionTestUtils.setField(request, "email", "e@e.com");
        ReflectionTestUtils.setField(request, "authCode", "code");
        ReflectionTestUtils.setField(request, "newPassword", "newPw");

        given(emailService.verifyCode("e@e.com", "code", EmailType.RESET_PASSWORD)).willReturn(true);

        Member member = Member.builder().loginId("realID").email("e@e.com").build();
        given(memberRepository.findByEmail("e@e.com")).willReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("비밀번호 재설정 성공")
    void resetPasswordSuccessTest() {
        PasswordResetRequest request = new PasswordResetRequest();
        ReflectionTestUtils.setField(request, "loginId", "user");
        ReflectionTestUtils.setField(request, "email", "e@e.com");
        ReflectionTestUtils.setField(request, "authCode", "code");
        ReflectionTestUtils.setField(request, "newPassword", "newPw");

        Member member = Member.builder().loginId("user").email("e@e.com").build();

        given(emailService.verifyCode("e@e.com", "code", EmailType.RESET_PASSWORD)).willReturn(true);
        given(memberRepository.findByEmail("e@e.com")).willReturn(Optional.of(member));
        given(passwordEncoder.encode("newPw")).willReturn("encoded");

        authService.resetPassword(request);

        assertThat(member.getPassword()).isEqualTo("encoded");
        verify(memberRepository).save(member);
    }
}