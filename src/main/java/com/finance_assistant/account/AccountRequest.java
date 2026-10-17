package com.finance_assistant.account;

import com.finance_assistant.account.enums.AccountType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull AccountType type,
        @NotNull @Digits(integer = 12, fraction = 2) BigDecimal openingBalance,
        @NotNull LocalDate openingDate) {
}
