package com.nhnacademy.member_server.service.impl;

import static com.nhnacademy.member_server.exception.ErrorCode.INVALID_INPUT_VALUE;
import static com.nhnacademy.member_server.exception.ErrorCode.MEMBER_NOT_FOUND;
import static com.nhnacademy.member_server.exception.ErrorCode.POINT_NOT_ENOUGH;
import static com.nhnacademy.member_server.exception.ErrorCode.POINT_NOT_ORDER_ID;
import static com.nhnacademy.member_server.exception.ErrorCode.POINT_POLICY_NOT_FOUND;

import com.nhnacademy.member_server.dto.request.PointAdminAdjustmentRequest;
import com.nhnacademy.member_server.dto.request.PointAdminPolicyRequest;
import com.nhnacademy.member_server.dto.request.PointEarnRequest;
import com.nhnacademy.member_server.dto.request.PointTransactionRequest;
import com.nhnacademy.member_server.dto.response.PointAdminPolicyResponse;
import com.nhnacademy.member_server.dto.response.PointBalanceResponse;
import com.nhnacademy.member_server.dto.response.PointHistoryResponse;
import com.nhnacademy.member_server.entity.PointEventType;
import com.nhnacademy.member_server.entity.PointHistory;
import com.nhnacademy.member_server.entity.PointPolicy;
import com.nhnacademy.member_server.entity.PointStatus;
import com.nhnacademy.member_server.entity.member.Member;
import com.nhnacademy.member_server.exception.BusinessException;
import com.nhnacademy.member_server.exception.ErrorCode;
import com.nhnacademy.member_server.repository.MemberRepository;
import com.nhnacademy.member_server.repository.PointHistoryRepository;
import com.nhnacademy.member_server.repository.PointPolicyRepository;
import com.nhnacademy.member_server.service.PointService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
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
                    throw new BusinessException(POINT_POLICY_NOT_FOUND);
                }

                pointToEarn = switch (requestDto.getEventType()) {
                    case EARN_REVIEW -> policy.getReviewPoint();
                    case EARN_PHOTO_REVIEW -> policy.getPhotoPoint();
                    case EARN_SIGNUP -> policy.getSignupPoint();
                    case EARN_REVIEW_UPGRADE -> {
                        long diff = policy.getPhotoPoint() - policy.getReviewPoint();
                        // 혹시 정책 변경으로 일반리뷰가 더 비싸지거나 같아지면 0원
                        yield Math.max(diff, 0L);
                    }
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
                    newPointBalance,
                    PointStatus.CONFIRMED
            ));
            return newPointBalance;
        }
        return member.getCurrentPoint();
    }

    @Override
    public Long usePoint(PointTransactionRequest requestDto) {
        return processUsePoint(requestDto, PointStatus.CONFIRMED);
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
                newPointBalance,
                PointStatus.CONFIRMED
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
        if (!memberRepository.existsById(memberId)) {
            throw new BusinessException(MEMBER_NOT_FOUND);
        }

        return pointHistoryRepository.findAllByMemberId(memberId, pageable)
                .map(PointHistoryResponse::from);
    }

    // 관리자용 메서드
    @Override
    @Transactional(readOnly = true)
    public PointAdminPolicyResponse getRecentPolicy(){
        PointPolicy policy = pointPolicyRepository.findTopByOrderByUpdatedAtDesc();

        if(policy == null){
            throw new BusinessException(ErrorCode.POINT_POLICY_NOT_FOUND);
        }

        return PointAdminPolicyResponse.from(policy);
    }

    @Override // 새 정책 insert
    public void updatePolicy(PointAdminPolicyRequest requestDto){
        pointPolicyRepository.save(
                PointPolicy.builder()
                        .signupPoint(requestDto.getSignupPoint())
                        .reviewPoint(requestDto.getReviewPoint())
                        .photoPoint(requestDto.getPhotoPoint())
                        .build()
        );
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
                newBalance,
                PointStatus.CONFIRMED
        ));

        return newBalance;
    }

    // [수정] TCC Reserve: 포인트 차감 후 'RESERVED' 상태로 저장
    @Override
    public void reservePoint(Long memberId, Long amount, Long orderId) {
        log.info("TCC Reserve 요청: memberId={}, amount={}, orderId={}", memberId, amount, orderId);

        // 멱등성 검사
        if (pointHistoryRepository.existsByOrderIdAndPointEventType(orderId, PointEventType.USE_ORDER)) {
            log.warn("이미 처리된 예약 요청입니다.: orderId={}", orderId);
            return;
        }

        PointTransactionRequest request = new PointTransactionRequest(memberId, amount, orderId);
        // 여기서 핵심! 상태를 RESERVED로 넘김
        processUsePoint(request, PointStatus.RESERVED);

        log.info("TCC Reserve(차감/예약) 완료: memberId={}, amount={}", memberId, amount);
    }

    // [수정] TCC Confirm: 'RESERVED' 상태를 'CONFIRMED'로 변경
    @Override
    public void confirmPoint(Long memberId, Long amount, Long orderId) {
        // 1. 예약 내역 조회
        PointHistory history = pointHistoryRepository.findByOrderIdAndPointEventType(orderId, PointEventType.USE_ORDER)
                .orElseThrow(() -> new BusinessException(ErrorCode.POINT_NOT_FOUND));

        // 2. 상태 검증
        if (history.getStatus() == PointStatus.CONFIRMED) {
            log.info("이미 확정된 주문입니다: orderId={}", orderId);
            return;
        }

        if (history.getStatus() != PointStatus.RESERVED) {
            // CANCELED 상태 등에서 Confirm이 들어오면 에러 혹은 무시
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE); // 적절한 에러코드 사용
        }

        // 3. 상태 변경 (DB 업데이트)
        history.updateStatus(PointStatus.CONFIRMED);

        log.info("TCC Confirm(확정) 완료: memberId={}, orderId={}", memberId, orderId);
    }

    // [수정] TCC Cancel: 'RESERVED' 상태일 때만 취소/환불 진행
    @Override
    public void cancelPoint(Long memberId, Long amount, Long orderId) {
        log.info("TCC Cancel 요청: memberId={}, orderId={}", memberId, orderId);

        // 1. 예약 내역 조회
        PointHistory history = pointHistoryRepository.findByOrderIdAndPointEventType(orderId, PointEventType.USE_ORDER)
                .orElseThrow(() -> {
                    log.warn("취소할 내역이 없습니다. orderId={}", orderId);
                    return new BusinessException(ErrorCode.POINT_NOT_FOUND);
                });

        // 2. 상태 검증 (CodeRabbit 지적 사항: Reserve된 것만 취소해야 함)
        if (history.getStatus() == PointStatus.CANCELED) {
            log.warn("이미 취소된 주문입니다: orderId={}", orderId);
            return;
        }

        if (history.getStatus() == PointStatus.CONFIRMED) {
            log.error("이미 확정(Confirm)된 주문은 TCC Cancel로 취소할 수 없습니다. (별도 반품 로직 필요): orderId={}", orderId);
            // 비즈니스 로직에 따라 여기서 에러를 뱉거나, return 하거나 선택
            return;
        }

        // 3. 환불 로직 수행 (포인트 되돌리기)
        PointTransactionRequest request = new PointTransactionRequest(memberId, amount, orderId);
        revertPoint(request); // 이 메서드는 'REVERT_ORDER' 타입의 히스토리를 새로 쌓습니다 (Status는 기본값 CONFIRMED)

        // 4. 원본 예약 내역 상태를 CANCELED로 변경
        history.updateStatus(PointStatus.CANCELED);

        log.info("TCC Cancel(환불) 완료: memberId={}, orderId={}", memberId, orderId);
    }



    private Long processUsePoint(PointTransactionRequest requestDto, PointStatus status) {
        validateTransactionRequest(requestDto);
        Member member = memberRepository.findByIdForUpdate(requestDto.getMemberId())
                .orElseThrow(() -> new BusinessException(MEMBER_NOT_FOUND));

        long amountUsedPoint = requestDto.getAmount();
        if (member.getCurrentPoint() < amountUsedPoint) {
            throw new BusinessException(POINT_NOT_ENOUGH);
        }

        // 잔액 차감
        long newPointBalance = member.getCurrentPoint() - amountUsedPoint;
        member.setCurrentPoint(newPointBalance);

        String description = String.format("%s (주문번호: %d)", PointEventType.USE_ORDER.getDescription(), requestDto.getOrderId());

        // History 저장 시 전달받은 Status 사용
        pointHistoryRepository.save(new PointHistory(
                requestDto.getOrderId(),
                member,
                -amountUsedPoint,
                description,
                PointEventType.USE_ORDER,
                newPointBalance,
                status // RESERVED or CONFIRMED
        ));

        return newPointBalance;
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
