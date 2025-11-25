package com.nhnacademy.member_server.service.impl;

import static com.nhnacademy.member_server.exception.ErrorCode.MEMBER_NOT_FOUND;
import static com.nhnacademy.member_server.exception.ErrorCode.POINT_NOT_ORDER_ID;
import static com.nhnacademy.member_server.exception.ErrorCode.POINT_NOT_POLICY;

import com.nhnacademy.member_server.dto.request.PointEarnRequest;
import com.nhnacademy.member_server.dto.request.PointTransactionRequest;
import com.nhnacademy.member_server.entity.Member;
import com.nhnacademy.member_server.entity.PointEventType;
import com.nhnacademy.member_server.entity.PointHistory;
import com.nhnacademy.member_server.entity.PointPolicy;
import com.nhnacademy.member_server.exception.BusinessException;
import com.nhnacademy.member_server.exception.ErrorCode;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.repository.PointHistoryRepository;
import com.nhnacademy.member_server.repository.PointPolicyRepository;
import com.nhnacademy.member_server.service.PointService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PointServiceImpl implements PointService {
    private final MemberRepository memberRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final PointPolicyRepository pointPolicyRepository;

    public Long earnPoint(PointEarnRequest requestDto){
        Member member = memberRepository.findById(requestDto.getMemberId()).orElseThrow(() -> new BusinessException(MEMBER_NOT_FOUND));
        long pointToEarn = 0;
        String description = "";
        Long orderIdToSave = null;

        // 상품 구매 시 등급별 비율 적용 적립
        if(requestDto.getEventType() == PointEventType.ORDER){
            BigDecimal rate = member.getGrade().getPointRate();

            if(requestDto.getPureAmount() != null){
                pointToEarn = BigDecimal.valueOf(requestDto.getPureAmount())
                        .multiply(rate)
                        .longValue();
            }
            if (requestDto.getOrderId() == null) {
                throw new BusinessException(POINT_NOT_ORDER_ID);
            }
            orderIdToSave = requestDto.getOrderId();
            description = String.format("상품 구매로 인한 포인트 적립 (주문번호: %d)", requestDto.getOrderId());
        }
        // 이벤트 (리뷰, 회원가입)
        else{
            PointPolicy policy  = pointPolicyRepository.findTopByOrderByUpdatedAtDesc();

            if(policy == null){
                throw new BusinessException(POINT_NOT_POLICY);
            } else{
                switch (requestDto.getEventType()){
                    case REVIEW ->  {
                        pointToEarn = policy.getReviewPoint();
                        description = "리뷰 작성 적립";
                    }
                    case PHOTO_REVIEW -> {
                        pointToEarn = policy.getPhotoPoint();
                        description = "사진 리뷰 작성 적립";
                    }
                    case SIGNUP -> {
                        pointToEarn = policy.getSignupPoint();
                        description = "회원가입 축하 적립";
                    }
                }
            }
        }

        // 실제 적립 로직
        if (pointToEarn > 0) {
            long newPointBalance = member.getCurrentPoint() + pointToEarn;
            member.setCurrentPoint(newPointBalance);

            pointHistoryRepository.save(new PointHistory(
                    orderIdToSave,
                    member,
                    pointToEarn,
                    description,
                    newPointBalance
            ));
            return newPointBalance;
        }
        return member.getCurrentPoint();
    }


    public Long usePoint(PointTransactionRequest requestDto){
        Member member = memberRepository.findById(requestDto.getMemberId()).orElseThrow(() -> new BusinessException(MEMBER_NOT_FOUND));
        long amountUsedPoint = requestDto.getAmount();
        String description = "";

        // 검증
        if(member.getCurrentPoint() < amountUsedPoint){
            throw new BusinessException(ErrorCode.POINT_NOT_ENOUGH);
        }

        // 잔액 차감
        long newPointBalance = member.getCurrentPoint() - amountUsedPoint;
        member.setCurrentPoint(newPointBalance);
        description = String.format("상품 구매로 인한 포인트 차감 (주문번호: %d)", requestDto.getOrderId());

        pointHistoryRepository.save(new PointHistory(
                requestDto.getOrderId(),
                member,
                -amountUsedPoint,
                description,
                newPointBalance
        ));
        return newPointBalance;
    }


    public Long revertPoint(PointTransactionRequest requestDto){
        Member member = memberRepository.findById(requestDto.getMemberId()).orElseThrow(() -> new BusinessException(MEMBER_NOT_FOUND));
        long amountRevertedPoint = requestDto.getAmount();
        String description = "";

        // 잔액 환불
        long newPointBalance = member.getCurrentPoint() + amountRevertedPoint;
        member.setCurrentPoint(newPointBalance);
        description = String.format("상품 환불 또는 취소로 인한 포인트 환불 (주문번호: %d)", requestDto.getOrderId());

        pointHistoryRepository.save(new PointHistory(
                requestDto.getOrderId(),
                member,
                amountRevertedPoint,
                description,
                newPointBalance
        ));
        return newPointBalance;
    }
}
