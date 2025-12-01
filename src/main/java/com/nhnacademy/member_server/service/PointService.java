package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.dto.request.PointAdminAdjustmentRequest;
import com.nhnacademy.member_server.dto.request.PointAdminPolicyRequest;
import com.nhnacademy.member_server.dto.request.PointEarnRequest;
import com.nhnacademy.member_server.dto.request.PointTransactionRequest;
import com.nhnacademy.member_server.dto.response.PointAdminPolicyResponse;
import com.nhnacademy.member_server.dto.response.PointBalanceResponse;
import com.nhnacademy.member_server.dto.response.PointHistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PointService {
    Long earnPoint(PointEarnRequest requestDto);
    Long usePoint(PointTransactionRequest requestDto);
    Long revertPoint(PointTransactionRequest requestDto);
    PointBalanceResponse getBalance(Long memberId);
    Page<PointHistoryResponse> getHistory(Long memberId, Pageable pageable);
    PointAdminPolicyResponse getRecentPolicy();
    void updatePolicy(PointAdminPolicyRequest requestDto);
    Long adjustmentMemberPoint(PointAdminAdjustmentRequest request);
}
