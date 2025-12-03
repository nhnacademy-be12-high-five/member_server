package com.nhnacademy.member_server.service.impl;

import static com.nhnacademy.member_server.exception.ErrorCode.INVALID_INPUT_VALUE;
import static com.nhnacademy.member_server.exception.ErrorCode.MEMBER_NOT_FOUND;
import static com.nhnacademy.member_server.exception.ErrorCode.POINT_NOT_ENOUGH;
import static com.nhnacademy.member_server.exception.ErrorCode.POINT_NOT_ORDER_ID;
import static com.nhnacademy.member_server.exception.ErrorCode.POINT_NOT_POLICY;

import com.nhnacademy.member_server.dto.request.PointAdminAdjustmentRequest;
import com.nhnacademy.member_server.dto.request.PointAdminPolicyRequest;
import com.nhnacademy.member_server.dto.request.PointEarnRequest;
import com.nhnacademy.member_server.dto.request.PointTransactionRequest;
import com.nhnacademy.member_server.dto.response.PointAdminPolicyResponse;
import com.nhnacademy.member_server.dto.response.PointBalanceResponse;
import com.nhnacademy.member_server.dto.response.PointHistoryResponse;
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
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PointServiceImpl implements PointService {
    private final MemberRepository memberRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final PointPolicyRepository pointPolicyRepository;

    @Override
    public Long earnPoint(PointEarnRequest requestDto){
        Member member = memberRepository.findByIdForUpdate(requestDto.getMemberId()).orElseThrow(() -> new BusinessException(MEMBER_NOT_FOUND));
        long pointToEarn;
        String description = requestDto.getEventType().getDescription();
        Long orderIdToSave = null;

        switch (requestDto.getEventType()){
            // 상품 구매 시 등급별 비율 적용 적립
            case EARN_ORDER -> {
                validateOrderRequest(requestDto);

                BigDecimal rate = member.getGrade().getPointRate();
                pointToEarn = BigDecimal.valueOf(requestDto.getPureAmount())
                        .multiply(rate)
                        .longValue();
                orderIdToSave = requestDto.getOrderId();
                description = String.format("%s (주문번호: %d)", description, requestDto.getOrderId());
            }
            // 상품 반품으로 인한 포인트 적립
            case EARN_REFUND -> {
                validateOrderRequest(requestDto);

                pointToEarn = requestDto.getPureAmount();
                orderIdToSave = requestDto.getOrderId();
                description = String.format("%s (주문번호: %d)", description, orderIdToSave);
            }
            default -> {
                PointPolicy policy = pointPolicyRepository.findTopByOrderByUpdatedAtDesc();
                if (policy == null) {
                    throw new BusinessException(POINT_NOT_POLICY);
                }

                pointToEarn = switch (requestDto.getEventType()) {
                    case EARN_REVIEW -> policy.getReviewPoint();
                    case EARN_PHOTO_REVIEW -> policy.getPhotoPoint();
                    case EARN_SIGNUP -> policy.getSignupPoint();
                    default -> 0L;
                };
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
                    requestDto.getEventType(),
                    newPointBalance
            ));
            return newPointBalance;
        }
        return member.getCurrentPoint();
    }

    @Override
    public Long usePoint(PointTransactionRequest requestDto){
        validateTransactionRequest(requestDto);

        Member member = memberRepository.findByIdForUpdate(requestDto.getMemberId()).orElseThrow(() -> new BusinessException(MEMBER_NOT_FOUND));

        long amountUsedPoint = requestDto.getAmount();

        if(member.getCurrentPoint() < amountUsedPoint){
            throw new BusinessException(POINT_NOT_ENOUGH);
        }

        // 잔액 차감
        long newPointBalance = member.getCurrentPoint() - amountUsedPoint;
        member.setCurrentPoint(newPointBalance);

        String description = String.format("%s (주문번호: %d)",PointEventType.USE_ORDER.getDescription(), requestDto.getOrderId());

        pointHistoryRepository.save(new PointHistory(
                requestDto.getOrderId(),
                member,
                -amountUsedPoint, // 사용 -> 음수저장
                description,
                PointEventType.USE_ORDER,
                newPointBalance
        ));
        return newPointBalance;
    }

    @Override
    public Long revertPoint(PointTransactionRequest requestDto){
        validateTransactionRequest(requestDto);

        Member member = memberRepository.findByIdForUpdate(requestDto.getMemberId()).orElseThrow(() -> new BusinessException(MEMBER_NOT_FOUND));

        long amountRevertedPoint = requestDto.getAmount();

        // 잔액 환불
        long newPointBalance = member.getCurrentPoint() + amountRevertedPoint;
        member.setCurrentPoint(newPointBalance);

        String description = String.format("%s (주문번호: %d)",PointEventType.REVERT_ORDER.getDescription(), requestDto.getOrderId());

        pointHistoryRepository.save(new PointHistory(
                requestDto.getOrderId(),
                member,
                amountRevertedPoint,
                description,
                PointEventType.REVERT_ORDER,
                newPointBalance
        ));
        return newPointBalance;
    }


    @Override
    @Transactional(readOnly = true)
    public PointBalanceResponse getBalance(Long memberId){
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new BusinessException(MEMBER_NOT_FOUND));
        Long totalEarned = pointHistoryRepository.sumEarnedPoints(memberId);

        if (totalEarned == null){
            totalEarned = 0L;
        }

        return new PointBalanceResponse(member.getId(), member.getCurrentPoint(), totalEarned);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PointHistoryResponse> getHistory(Long memberId, Pageable pageable){
        memberRepository.findById(memberId).orElseThrow(() -> new BusinessException(MEMBER_NOT_FOUND));

        Page<PointHistory> historyPage = pointHistoryRepository.findAllByMemberId(memberId, pageable);

        // Page 객체의 map 메서드로 리스트 안의 내용물을 하나씩 바꿈
        return historyPage.map(entity -> new PointHistoryResponse(
                entity.getId(),
                entity.getAmount(),
                entity.getDescription(),
                entity.getPointBalance(), // DTO - currentPoint
                entity.getCreatedAt(),    // DTO - transactionDate
                entity.getOrderId()
        ));
    }

    // 관리자용 메서드
    @Override
    @Transactional(readOnly = true)
    public PointAdminPolicyResponse getRecentPolicy(){
        PointPolicy policy = pointPolicyRepository.findTopByOrderByUpdatedAtDesc();

        if(policy == null){
            throw new BusinessException(ErrorCode.POINT_NOT_POLICY);
        }

        return PointAdminPolicyResponse.builder()
                .signupPoint(policy.getSignupPoint())
                .reviewPoint(policy.getReviewPoint())
                .photoPoint(policy.getPhotoPoint())
                .build();
    }

    @Override // 새 정책 insert
    public void updatePolicy(PointAdminPolicyRequest requestDto){
        pointPolicyRepository.save(new PointPolicy(
                null,
                LocalDateTime.now(),
                requestDto.getSignupPoint(),
                requestDto.getReviewPoint(),
                requestDto.getPhotoPoint()
        ));
    }

    @Override
    public Long adjustmentMemberPoint(PointAdminAdjustmentRequest requestDto) {
        Member member = memberRepository.findByIdForUpdate(requestDto.getMemberId()).orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        long amount = requestDto.getAmount();
        if (amount == 0) {
            throw new BusinessException(INVALID_INPUT_VALUE);
        }

        PointEventType eventType;

        if (amount > 0) {
            eventType = PointEventType.EARN_ADMIN;
        } else {
            // 사용자 포인트 음수 안되게 막기
            if (member.getCurrentPoint() < Math.abs(amount)) {
                throw new BusinessException(ErrorCode.POINT_NOT_ENOUGH);
            }
            eventType = PointEventType.USE_ADMIN;
        }

        long newBalance = member.getCurrentPoint() + amount;
        member.setCurrentPoint(newBalance);

        String description = String.format("%s (사유: %s)", eventType.getDescription(), requestDto.getReason());

        pointHistoryRepository.save(new PointHistory(
                null,
                member,
                amount,
                description,
                eventType,
                newBalance
        ));

        return newBalance;
    }

    // 검증 메서드
    private void validateOrderRequest(PointEarnRequest requestDto) {
        if (requestDto.getPureAmount() == null || requestDto.getOrderId() == null) {
            throw new BusinessException(INVALID_INPUT_VALUE);
        }
    }

    private void validateTransactionRequest(PointTransactionRequest requestDto) {
        if (requestDto.getOrderId() == null) {
            throw new BusinessException(POINT_NOT_ORDER_ID);
        }
        if (requestDto.getAmount() == null || requestDto.getAmount() <= 0) {
            throw new BusinessException(INVALID_INPUT_VALUE);
        }
    }
}
