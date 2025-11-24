package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.dto.request.PointEarnRequest;
import com.nhnacademy.member_server.dto.request.PointTransactionRequest;
import com.nhnacademy.member_server.entity.Member;
import com.nhnacademy.member_server.entity.PointEventType;
import com.nhnacademy.member_server.entity.PointHistory;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.repository.PointHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PointService {
    private final MemberRepository memberRepository;
    private final PointHistoryRepository pointHistoryRepository;


    // 포인트 적립
    public Long earnPoint(PointEarnRequest requestDto){
        Member member = memberRepository.findById(requestDto.getMemberId()).orElseThrow(() -> new RuntimeException("존재하지 않는 유저 입니다."));
        long pointToEarn = 0;
        String description = "";

        // 상품 구매 시 등급별 비율 적용 적립
        if(requestDto.getEventType() == PointEventType.ORDER){

        }


        // 이벤트 (리뷰, 회원가입)


        return 0L;
    }


    public Long usePoint(PointTransactionRequest requestDto){
        Member member = memberRepository.findById(requestDto.getMemberId()).orElseThrow(() -> new RuntimeException("존재하지 않는 유저 입니다."));
        long amountUsedPoint = requestDto.getAmount();

        // 검증
        if(member.getCurrentPoint() < amountUsedPoint){
            throw new RuntimeException("포인트 잔액이 부족합니다");
        }

        // 잔액 차감
        long newPointBalance = member.getCurrentPoint() - amountUsedPoint;
        member.setCurrentPoint(newPointBalance);

        pointHistoryRepository.save(new PointHistory(
                null,
                member,
                amountUsedPoint,
                requestDto.getDescription(),
                newPointBalance
        ));
        return newPointBalance;
    }


    public Long revertPoint(PointTransactionRequest requestDto){
        Member member = memberRepository.findById(requestDto.getMemberId()).orElseThrow(() -> new RuntimeException("존재하지 않는 유저 입니다."));
        long amountRevertedPoint = requestDto.getAmount();

        // 잔액 환불
        long newPointBalance = member.getCurrentPoint() + amountRevertedPoint;
        member.setCurrentPoint(newPointBalance);

        pointHistoryRepository.save(new PointHistory(
                null,
                member,
                amountRevertedPoint,
                "결제 취소/환불",
                newPointBalance
        ));
        return newPointBalance;
    }
}
