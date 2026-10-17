package com.finance_assistant.account.enums;

public enum AccountType {
    BANK_ACCOUNT,
    CREDIT_CARD,
    PAYLATER;

    public boolean isDebt() {
        return this == CREDIT_CARD || this == PAYLATER;
    }
}
