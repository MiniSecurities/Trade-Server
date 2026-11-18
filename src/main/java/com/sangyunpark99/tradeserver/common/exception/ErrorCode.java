package com.sangyunpark99.tradeserver.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_BALANCE(HttpStatus.BAD_REQUEST, "현금 잔고는 0 이상이어야 합니다."),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "금액은 0보다 커야 합니다."),
    INSUFFICIENT_AVAILABLE_BALANCE(HttpStatus.CONFLICT, "주문 가능 금액이 부족합니다."),
    EXCEED_RESERVED_BALANCE(HttpStatus.INTERNAL_SERVER_ERROR, "금액이 묶인 금액보다 큽니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
