package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.dto.request.PointEarnRequest;
import com.nhnacademy.member_server.dto.request.PointTransactionRequest;
import com.nhnacademy.member_server.dto.response.PointTransactionResponse;
import com.nhnacademy.member_server.service.impl.PointServiceImpl;
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

    private final PointServiceImpl pointServiceImpl;

    @PostMapping("/earn")
    public ResponseEntity<PointTransactionResponse> earnPoint(@RequestBody PointEarnRequest requestDto){
        Long remainingBalance = pointServiceImpl.earnPoint(requestDto);

        return ResponseEntity.ok(new PointTransactionResponse(
                requestDto.getMemberId(),
                remainingBalance
        ));
    }

    @PostMapping("/use")
    public ResponseEntity<PointTransactionResponse> usePoint(@RequestBody PointTransactionRequest requestDto){

        PointTransactionResponse responseDto = new PointTransactionResponse(requestDto.getMemberId(), pointServiceImpl.usePoint(requestDto));

        return ResponseEntity.ok(responseDto);
    }

    @PostMapping("/revert")
    public ResponseEntity<PointTransactionResponse> revertPoint(@RequestBody PointTransactionRequest requestDto){

        PointTransactionResponse responseDto = new PointTransactionResponse(requestDto.getMemberId(), pointServiceImpl.revertPoint(requestDto));

        return ResponseEntity.ok(responseDto);
    }
}
