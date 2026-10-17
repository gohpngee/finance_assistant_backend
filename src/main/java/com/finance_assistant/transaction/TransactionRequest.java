package com.finance_assistant.transaction;

import com.finance_assistant.transaction.enums.SpendingCategory;
import com.finance_assistant.transaction.enums.TransactionKind;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
        @NotNull TransactionKind kind,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @NotNull LocalDate transactionDate,
        @NotBlank @Size(max = 250) String description,
        SpendingCategory category,
        @Positive Long sourceAccountId,
        @Positive Long destinationAccountId,
        @Size(max = 200) String sourceMessageKey) {
}
