package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.dto.request.PointEarnRequest;
import com.nhnacademy.member_server.dto.request.PointTransactionRequest;
import com.nhnacademy.member_server.dto.response.PointTransactionResponse;
import com.nhnacademy.member_server.service.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/points")
public class PointInternalController implements PointInternalSwagger{

    private final PointService pointService;

    @PostMapping("/earn")
    public ResponseEntity<PointTransactionResponse> earnPoint(@RequestBody PointEarnRequest requestDto){

        PointTransactionResponse responseDto = new PointTransactionResponse(requestDto.getMemberId(), pointService.earnPoint(requestDto));

        return ResponseEntity.ok(responseDto);
    }

    @PostMapping("/use")
    public ResponseEntity<PointTransactionResponse> usePoint(@RequestBody PointTransactionRequest requestDto){

        PointTransactionResponse responseDto = new PointTransactionResponse(requestDto.getMemberId(), pointService.usePoint(requestDto));

        return ResponseEntity.ok(responseDto);
    }

    @PostMapping("/revert")
    public ResponseEntity<PointTransactionResponse> revertPoint(@RequestBody PointTransactionRequest requestDto){

        PointTransactionResponse responseDto = new PointTransactionResponse(requestDto.getMemberId(), pointService.revertPoint(requestDto));

        return ResponseEntity.ok(responseDto);
    }
}
