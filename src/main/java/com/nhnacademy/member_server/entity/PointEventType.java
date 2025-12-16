package com.nhnacademy.member_server.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PointEventType {

    EARN_ORDER("상품 구매 적립"),
    EARN_REVIEW("일반 리뷰 작성 적립"),
    EARN_PHOTO_REVIEW("포토 리뷰 작성 적립"),
    EARN_REVIEW_UPGRADE("리뷰 수정 추가 적립 (일반->포토)"),
    EARN_SIGNUP("회원가입 축하 적립"),
    EARN_REFUND("상품 반품으로 인한 적립"),  // (반품시 돈 대신 포인트로)
    EARN_ADMIN("관리자에 의한 적립"),

    USE_ORDER("상품 결제 사용"),
    USE_ADMIN("관리자에 의한 차감"),

    REVERT_ORDER("결제 취소로 인한 사용 포인트 복구");  // USE_ORDER와 짝을 이루어 상쇄하는 용도

    private final String description;
}