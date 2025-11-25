package com.nhnacademy.member_server.service;

import com.nhnacademy.member_server.dto.request.PointEarnRequest;
import com.nhnacademy.member_server.dto.request.PointTransactionRequest;

public interface PointService {
    Long earnPoint(PointEarnRequest requestDto);
    Long usePoint(PointTransactionRequest requestDto);
    Long revertPoint(PointTransactionRequest requestDto);
}
