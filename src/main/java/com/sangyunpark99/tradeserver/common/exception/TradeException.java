package com.sangyunpark99.tradeserver.common.exception;

import lombok.Getter;

@Getter
public class TradeException extends RuntimeException {

    private final ErrorCode errorCode;

    public TradeException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
