package com.nhnacademy.member_server.scheduler;

import com.nhnacademy.member_server.entity.member.Grade;
import com.nhnacademy.member_server.entity.member.Member;
import com.nhnacademy.member_server.entity.member.Role;
import com.nhnacademy.member_server.entity.member.Status;
import com.nhnacademy.member_server.feign.OrderFeignClient;
import com.nhnacademy.member_server.repository.GradeRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.service.CartService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"dormant-test", "test"})
@Transactional
class DormantMemberSchedulerTest {

    @Autowired
    private DormantMemberScheduler scheduler;

    @MockBean
    private OrderFeignClient orderFeignClient;

    @MockBean
    private CartService cartService;

    @MockBean
    private CartTTLScanScheduler cartTTLScanScheduler;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private GradeRepository gradeRepository;

    @Test
    @DisplayName("3개월 이상 미접속 회원은 휴면 상태로 변경되어야 한다 (페이징 처리 포함)")
    void processDormantMembersTest_withPaging() {
        // given
        LocalDateTime now = LocalDateTime.now();

        Grade testGrade = Grade.builder()
                .gradeName("GENERAL")
                .min(0)
                .pointRate(new BigDecimal("0.01"))
                .build();
        gradeRepository.save(testGrade);

        // 휴면 대상: (4개월 전 로그인) - 페이징을 타도록 120명 생성 (페이지 사이즈 100 가정)
        int dormantTargetCount = 120;
        for (int i = 0; i < dormantTargetCount; i++) {
            Member targetMember = Member.builder()
                    .loginId("targetUser" + i)
                    .email("target" + i + "@test.com")
                    .name("타겟유저" + i)
                    .phone("010-1111-" + String.format("%04d", i))
                    .status(Status.ACTIVE)
                    .lastLoginAt(now.minusMonths(4))
                    .password("dummyPassword123!")
                    .birthDate(LocalDate.of(1990, 1, 1))
                    .grade(testGrade)
                    .currentPoint(0L)
                    .role(Role.USER)
                    .build();

            memberRepository.save(targetMember);
        }

        // 활성 대상: (1개월 전 로그인)
        Member activeMember = Member.builder()
                .loginId("activeUser")
                .email("active@test.com")
                .name("활성유저")
                .phone("010-2222-2222")
                .status(Status.ACTIVE)
                .lastLoginAt(now.minusMonths(1))
                .password("dummyPassword123!")
                .birthDate(LocalDate.of(2000, 12, 25))
                .grade(testGrade)
                .currentPoint(0L)
                .role(Role.USER)
                .build();
        memberRepository.save(activeMember);

        // when
        scheduler.processDormantMembers();

        // then
        List<Member> all = memberRepository.findAll();

        long dormantCount = all.stream()
                .filter(m -> m.getLoginId().startsWith("targetUser"))
                .filter(m -> m.getStatus() == Status.DORMANT)
                .count();

        Member resultActive = memberRepository.findById(activeMember.getId()).orElseThrow();

        assertThat(dormantCount).isEqualTo(dormantTargetCount);
        assertThat(resultActive.getStatus()).isEqualTo(Status.ACTIVE);
    }
}