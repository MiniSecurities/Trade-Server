package com.sangyunpark99.tradeserver.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),

    INVALID_BALANCE(HttpStatus.BAD_REQUEST, "현금 잔고는 0 이상이어야 합니다."),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "금액은 0보다 커야 합니다."),
    INSUFFICIENT_AVAILABLE_BALANCE(HttpStatus.CONFLICT, "주문 가능 금액이 부족합니다."),
    EXCEED_RESERVED_BALANCE(HttpStatus.INTERNAL_SERVER_ERROR, "금액이 묶인 금액보다 큽니다."),

    INVALID_SYMBOL(HttpStatus.BAD_REQUEST, "종목이 필요합니다."),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "주문 수량은 0보다 커야 합니다."),
    INVALID_LIMIT_PRICE(HttpStatus.BAD_REQUEST, "지정가는 0보다 커야 합니다."),

    INVALID_EMAIL(HttpStatus.BAD_REQUEST, "이메일이 필요합니다."),
    INVALID_NAME(HttpStatus.BAD_REQUEST, "이름이 필요합니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 필요합니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),

    INVALID_FILL_QUANTITY(HttpStatus.INTERNAL_SERVER_ERROR, "체결 수량은 0보다 커야 합니다."),
    INVALID_FILL_PRICE(HttpStatus.INTERNAL_SERVER_ERROR, "체결가는 0보다 커야 합니다."),
    FILL_PRICE_EXCEEDS_LIMIT(HttpStatus.INTERNAL_SERVER_ERROR, "체결가가 지정가보다 높습니다."),
    OVERFILL(HttpStatus.INTERNAL_SERVER_ERROR, "누적 체결 수량이 주문 수량을 넘습니다."),
    INVALID_FILL_ID(HttpStatus.INTERNAL_SERVER_ERROR, "체결 ID가 필요합니다."),

    NOT_FOUND_ACCOUNT(HttpStatus.NOT_FOUND, "계좌가 존재하지 않습니다."),
    NOT_FOUND_ORDER(HttpStatus.NOT_FOUND, "주문이 존재하지 않습니다."),

    FILL_ORDER_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "체결 통보된 주문이 존재하지 않습니다."),
    FILL_ACCOUNT_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "체결된 주문의 계좌가 존재하지 않습니다."),
    FILL_ON_REJECTED_ORDER(HttpStatus.INTERNAL_SERVER_ERROR, "거절된 주문에 체결이 통보되었습니다."),
    INVALID_ORDER_STATUS(HttpStatus.INTERNAL_SERVER_ERROR, "현재 주문 상태에서는 할 수 없는 변경입니다."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류입니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
