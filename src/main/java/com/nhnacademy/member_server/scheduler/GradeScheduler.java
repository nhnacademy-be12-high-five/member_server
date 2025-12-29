package com.nhnacademy.member_server.scheduler;

import com.nhnacademy.member_server.entity.member.Grade;
import com.nhnacademy.member_server.entity.member.Member;
import com.nhnacademy.member_server.entity.member.Status;
import com.nhnacademy.member_server.feign.OrderFeignClient;
import com.nhnacademy.member_server.repository.GradeRepository;
import com.nhnacademy.member_server.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GradeScheduler {

    private final MemberRepository memberRepository;
    private final GradeRepository gradeRepository;
    private final OrderFeignClient orderClient;

    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(
            name = "gradeUpdateScheduler",
            lockAtMostFor = "10m",
            lockAtLeastFor = "1m"
    )
    @Transactional
    public void updateMemberGrades() {
        log.info("[Scheduler] 회원 등급 산정 시작");

        LocalDateTime since = LocalDateTime.now().minusMonths(3);
        List<Grade> allGrades = gradeRepository.findAll();

        List<Member> activeMembers = memberRepository.findByLastLoginAtBeforeAndStatus(
                LocalDateTime.now().plusDays(1),
                Status.ACTIVE
        );

        int count = 0;

        for (Member member : activeMembers) {
            try {
                Long totalAmount = 0L;
                try {
                    totalAmount = orderClient.getTotalAmount(member.getId(), since).getBody();
                } catch (Exception e) {
                    log.error("주문 서버 통신 실패 (회원 ID: {}). ", member.getId());
                    continue;
                }
                if (totalAmount == null) totalAmount = 0L;

                Grade newGrade = matchGrade(totalAmount, allGrades);

                if (!member.getGrade().getId().equals(newGrade.getId())) {
                    log.info("회원 {} 등급 변경: {} -> {} (실적: {})",
                            member.getId(), member.getGrade().getGradeName(), newGrade.getGradeName(), totalAmount);
                    
                    member.setGrade(newGrade);
                    count++;
                }

            } catch (Exception e) {
                log.error("회원 {} 등급 산정 중 오류 발생", member.getId(), e);
            }
        }

        log.info("[Scheduler] 회원 등급 산정 완료. 총 {}명 등급 변경됨.", count);
    }

    private Grade matchGrade(Long amount, List<Grade> grades) {
        grades.sort((g1, g2) -> Integer.compare(g2.getMin(), g1.getMin()));

        for (Grade grade : grades) {
            if (amount >= grade.getMin()) {
                return grade;
            }
        }

        return grades.get(grades.size() - 1);
    }
}