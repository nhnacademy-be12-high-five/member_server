package com.nhnacademy.member_server.scheduler;

import com.nhnacademy.member_server.entity.member.Grade;
import com.nhnacademy.member_server.entity.member.Member;
import com.nhnacademy.member_server.entity.member.Status;
import com.nhnacademy.member_server.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DormantMemberSchedulerTest {

    @InjectMocks
    private DormantMemberScheduler scheduler;

    @Mock
    private MemberRepository memberRepository;

    @Test
    @DisplayName("3개월 이상 미접속 회원은 휴면 상태로 변경되어야 한다")
    void processDormantMembersTest() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Grade testGrade = Grade.builder().gradeName("GENERAL").build();

        List<Member> targetMembers = new ArrayList<>();
        int dormantTargetCount = 10;
        for (int i = 0; i < dormantTargetCount; i++) {
            targetMembers.add(Member.builder()
                    .loginId("target" + i)
                    .status(Status.ACTIVE)
                    .lastLoginAt(now.minusMonths(4))
                    .grade(testGrade)
                    .build());
        }

        Page<Member> mockPage = new PageImpl<>(targetMembers);

        when(memberRepository.findByLastLoginAtBeforeAndStatus(
                any(LocalDateTime.class),
                eq(Status.ACTIVE),
                any(Pageable.class))
        ).thenReturn(mockPage)
                .thenReturn(Page.empty());

        scheduler.processDormantMembers();
        for (Member member : targetMembers) {
            assertThat(member.getStatus()).isEqualTo(Status.DORMANT);
        }

    }
}