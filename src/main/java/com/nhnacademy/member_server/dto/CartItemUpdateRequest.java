package com.nhnacademy.member_server.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemUpdateRequest(@NotNull Long bookId, @Min(1) int quantity) {}

