package com.nhnacademy.member_server.service.impl.member;

import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.service.member.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Slf4j
@Service // 스프링 빈 등록 (중요)
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final MemberRepository memberRepository;

    private static final long LIMIT_TIME = 3 * 60;
    private static final String PREFIX = "EMAIL_CERT:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    @Override
    public void sendVerificationCode(String email) {
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("이메일 주소가 비어있습니다.");
        }

        if (memberRepository.existsByEmail(email)) {
            throw new IllegalStateException("이미 가입된 이메일입니다.");
        }

        String code = createRandomCode();

        redisTemplate.opsForValue().set(PREFIX + email, code, Duration.ofSeconds(LIMIT_TIME));
        log.info("인증번호 생성 및 Redis 저장 완료: email={}", email);

        sendMail(email, code);
    }

    @Override
    public boolean verifyCode(String email, String inputCode) {
        if (email == null || inputCode == null) {
            return false;
        }

        String storedCode = redisTemplate.opsForValue().get(PREFIX + email);

        if (storedCode != null && storedCode.equals(inputCode)) {
            redisTemplate.delete(PREFIX + email);
            return true;
        }
        return false;
    }


    private void sendMail(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[HighFive] 이메일 인증 번호입니다.");
        message.setText("안녕하세요,\n\n요청하신 인증 번호는 [" + code + "] 입니다.\n3분 내에 입력해 주세요.");
        
        try {
            mailSender.send(message);
            log.info("메일 발송 성공: {}", email);
        } catch (Exception e) {
            log.error("메일 발송 실패", e);
            throw new RuntimeException("메일 발송 중 오류가 발생했습니다.");
        }
    }

    private String createRandomCode() {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            key.append(SECURE_RANDOM.nextInt(10));
        }
        return key.toString();
    }
}
