package com.finance_assistant.transaction;

import com.finance_assistant.transaction.enums.SpendingCategory;
import com.finance_assistant.transaction.enums.TransactionKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransactionResponse(
        Long id, TransactionKind kind, BigDecimal amount, LocalDate transactionDate,
        String description, SpendingCategory category, Long sourceAccountId,
        Long destinationAccountId, String sourceMessageKey, Instant createdAt) {
}
