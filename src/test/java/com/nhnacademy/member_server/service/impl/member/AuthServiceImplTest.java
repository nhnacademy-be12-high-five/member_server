package com.nhnacademy.member_server.service.impl.member;

import com.nhnacademy.member_server.dto.request.member.MemberCreateRequest;
import com.nhnacademy.member_server.dto.response.member.TokenDto;
import com.nhnacademy.member_server.entity.member.Gender;
import com.nhnacademy.member_server.entity.member.Grade;
import com.nhnacademy.member_server.entity.member.Member;
import com.nhnacademy.member_server.entity.member.Role;
import com.nhnacademy.member_server.entity.member.Status;
import com.nhnacademy.member_server.global.jwt.JwtUtil;
import com.nhnacademy.member_server.repository.GradeRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.security.UserDetailsImpl;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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

    @Test
    @DisplayName("로그인 성공 및 Redis 저장")
    void loginUserSuccess() {
        String loginId = "user";
        String password = "pw";
        Member member = Member.builder().id(1L).loginId(loginId).role(Role.USER).build();
        Authentication authentication = new UsernamePasswordAuthenticationToken(new UserDetailsImpl(member), null);

        given(authenticationManager.authenticate(any())).willReturn(authentication);
        given(memberRepository.findById(any())).willReturn(Optional.of(member));
        given(jwtUtil.createAccessToken(any(), any())).willReturn("access-token");
        given(jwtUtil.createRefreshToken(any())).willReturn("refresh-token");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        ReflectionTestUtils.setField(authService, "refreshExpirationTime", 1000L);

        TokenDto result = authService.loginUser(loginId, password);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        verify(redisTemplate.opsForValue()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("회원가입 성공 및 웰컴 쿠폰 메시지 발행")
    void signupSuccess() {

        MemberCreateRequest request = MemberCreateRequest.builder()
                .loginId("newuser").password("pw").name("신규").phone("01012341234")
                .email("new@test.com").gender(Gender.MALE).birthDate(LocalDate.now())
                .build();

        given(memberRepository.existsByLoginId(any())).willReturn(false);
        given(memberRepository.existsByEmail(any())).willReturn(false);
        given(memberRepository.existsByPhone(any())).willReturn(false);

        given(gradeRepository.findByGradeName("GENERAL")).willReturn(Optional.of(
                Grade.builder().gradeName("GENERAL").pointRate(BigDecimal.ZERO).build()
        ));
        given(passwordEncoder.encode(any())).willReturn("encodedPw");

        Member savedMember = Member.builder().id(1L).build();
        given(memberRepository.save(any(Member.class))).willReturn(savedMember);


        authService.signup(request);


        verify(memberRepository).save(any(Member.class));
        verify(rabbitTemplate).convertAndSend(eq("high-five-coupon-welcome-queue"), any(Object.class));
    }

    @Test
    @DisplayName("토큰 재발급 성공")
    void reissueSuccess() {

        String refreshToken = "valid-refresh-token";
        Long memberId = 1L;
        Member member = Member.builder().id(memberId).status(Status.ACTIVE).role(Role.USER).build();

        given(jwtUtil.validateToken(refreshToken)).willReturn(true);
        given(jwtUtil.getUserId(refreshToken)).willReturn(memberId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("RT:" + memberId)).willReturn(refreshToken);
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        given(jwtUtil.createAccessToken(any(), any())).willReturn("new-access");
        given(jwtUtil.createRefreshToken(any())).willReturn("new-refresh");

        ReflectionTestUtils.setField(authService, "refreshExpirationTime", 1000L);

        TokenDto result = authService.reissue(refreshToken);

        assertThat(result.getAccessToken()).isEqualTo("new-access");
    }
}