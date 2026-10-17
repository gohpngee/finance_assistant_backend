package com.finance_assistant.summary;

import com.finance_assistant.transaction.enums.SpendingCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record SummaryResponse(
        LocalDate from, LocalDate to, BigDecimal income, BigDecimal expenses,
        BigDecimal net, BigDecimal transfers, Map<SpendingCategory, BigDecimal> expensesByCategory) {
}
