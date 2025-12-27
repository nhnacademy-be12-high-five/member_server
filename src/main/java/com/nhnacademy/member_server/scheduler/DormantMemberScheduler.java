package com.nhnacademy.member_server.scheduler;

import com.nhnacademy.member_server.entity.member.Member;
import com.nhnacademy.member_server.entity.member.Status;
import com.nhnacademy.member_server.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DormantMemberScheduler {

    private final MemberRepository memberRepository;

    @Scheduled(cron = "0 0 0 * * *")
    @SchedulerLock(
            name = "dormantMemberScheduler",
            lockAtMostFor = "10m",
            lockAtLeastFor = "1m"
    )
    @Transactional
    public void processDormantMembers() {

        log.info("[Scheduler] 휴면 회원 전환 배치 시작");

        LocalDateTime cutOffDate = LocalDateTime.now().minusMonths(3);

        int size = 100;
        int totalCount = 0;

        while (true) {
            Page<Member> page =
                    memberRepository.findByLastLoginAtBeforeAndStatus(
                            cutOffDate,
                            Status.ACTIVE,
                            PageRequest.of(0, size)
                    );

            if (!page.hasContent()) {
                break;
            }

            for (Member member : page.getContent()) {
                member.setStatus(Status.DORMANT);
                totalCount++;
            }
        }

        if (totalCount == 0) {
            log.info("휴면 전환 대상 회원이 없습니다.");
            return;
        }

        log.info("총 {}명의 회원이 휴면 상태(DORMANT)로 전환되었습니다.", totalCount);
    }
}