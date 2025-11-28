package com.nhnacademy.member_server.controller;

import com.nhnacademy.member_server.dto.request.PointEarnRequest;
import com.nhnacademy.member_server.dto.request.PointTransactionRequest;
import com.nhnacademy.member_server.dto.response.PointTransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Point Internal API", description = "다른 서버에서 호출할 포인트 처리 API")
public interface PointInternalSwagger {

    @Operation(summary = "포인트 적립", description = "주문 완료 / 리뷰 작성 / 회원가입 시 포인트를 적립합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "적립 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 회원"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    ResponseEntity<PointTransactionResponse> earnPoint(@RequestBody PointEarnRequest requestDto);

    @Operation(summary = "포인트 사용 (차감)", description = "최종 결제 전 포인트를 차감합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "차감 성공 (남은 잔액 반환)"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (포인트 잔액 부족)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 회원"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    ResponseEntity<PointTransactionResponse> usePoint(@RequestBody PointTransactionRequest requestDto);


    @Operation(summary = "포인트 환불 (롤백)", description = "결제 실패 또는 취소 시 사용했던 포인트를 복구합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "환불 성공 (남은 잔액 반환)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 회원"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    ResponseEntity<PointTransactionResponse> revertPoint(@RequestBody PointTransactionRequest requestDto);
}
