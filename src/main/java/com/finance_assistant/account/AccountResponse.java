package com.finance_assistant.account;

import com.finance_assistant.account.enums.AccountType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AccountResponse(
        Long id, String name, AccountType type, BigDecimal openingBalance,
        LocalDate openingDate, BigDecimal balance, LocalDate balanceAsOf,
        boolean active, Instant createdAt) {
}
