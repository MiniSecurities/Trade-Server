package com.sangyunpark99.tradeserver.auth;

import com.sangyunpark99.tradeserver.common.exception.ErrorCode;
import com.sangyunpark99.tradeserver.common.exception.TradeException;

public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {

    }

    public static void set(long userId) {
        USER_ID.set(userId);
    }

    public static long get() {
        Long id = USER_ID.get();
        if(id == null) {
            throw new TradeException(ErrorCode.UNAUTHORIZED);
        }
        return id;
    }

    public static void remove() {
        USER_ID.remove();
    }
}