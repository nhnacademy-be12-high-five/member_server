package com.nhnacademy.member_server.dto.cartRequest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CartAddRequest(Long bookId, int quantity) {}
