package com.nhnacademy.member_server.service.impl.member;

import com.nhnacademy.member_server.entity.member.EmailType;
import com.nhnacademy.member_server.exception.BusinessException;
import com.nhnacademy.member_server.exception.ErrorCode;
import com.nhnacademy.member_server.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @InjectMocks
    EmailServiceImpl emailService;

    @Mock
    JavaMailSender mailSender;
    @Mock
    StringRedisTemplate redisTemplate;
    @Mock
    MemberRepository memberRepository;
    @Mock
    ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("회원가입 인증메일 발송 - 중복된 이메일 실패")
    void sendSignupCodeDuplicateFailTest() {
        String email = "exist@test.com";
        given(memberRepository.existsByEmail(email)).willReturn(true);

        assertThatThrownBy(() -> emailService.sendVerificationCode(email, EmailType.SIGNUP))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    @DisplayName("비밀번호 재설정 인증메일 발송 - 존재하지 않는 이메일 실패")
    void sendResetPasswordCodeNotFoundFailTest() {
        String email = "notfound@test.com";
        given(memberRepository.existsByEmail(email)).willReturn(false);

        assertThatThrownBy(() -> emailService.sendVerificationCode(email, EmailType.RESET_PASSWORD))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("인증메일 발송 성공")
    void sendVerificationCodeSuccessTest() {
        String email = "new@test.com";
        given(memberRepository.existsByEmail(email)).willReturn(false);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        emailService.sendVerificationCode(email, EmailType.SIGNUP);

        String key = EmailType.SIGNUP.getPrefix() + email;
        verify(valueOperations).set(eq(key), any(String.class), any(Duration.class));

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("인증코드 검증 성공")
    void verifyCodeSuccessTest() {
        String email = "test@test.com";
        String code = "123456";
        String key = EmailType.SIGNUP.getPrefix() + email;

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(key)).willReturn(code);

        boolean result = emailService.verifyCode(email, code, EmailType.SIGNUP);

        assertThat(result).isTrue();
        verify(redisTemplate).delete(key);
    }

    @Test
    @DisplayName("인증코드 검증 실패 - 코드 불일치")
    void verifyCodeMismatchTest() {
        String email = "test@test.com";
        String key = EmailType.SIGNUP.getPrefix() + email;

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(key)).willReturn("123456");

        boolean result = emailService.verifyCode(email, "000000", EmailType.SIGNUP);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("인증코드 검증 실패 - 만료된 코드(Redis에 없음)")
    void verifyCodeExpiredTest() {
        String email = "test@test.com";
        String key = EmailType.SIGNUP.getPrefix() + email;

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(key)).willReturn(null);

        boolean result = emailService.verifyCode(email, "123456", EmailType.SIGNUP);

        assertThat(result).isFalse();
    }
}