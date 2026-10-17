package com.finance_assistant.common;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class Money {
    private Money() {
    }

    public static long toSen(BigDecimal amount) {
        try {
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Amount must fit in integer sen and have at most two decimal places.");
        }
    }

    public static BigDecimal toMyr(long amountSen) {
        return BigDecimal.valueOf(amountSen, 2);
    }
}
