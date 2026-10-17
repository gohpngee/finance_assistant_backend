package com.finance_assistant.summary;

import com.finance_assistant.common.Money;
import com.finance_assistant.transaction.TransactionRepository;
import com.finance_assistant.transaction.enums.SpendingCategory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class SummaryService {
    private final TransactionRepository transactions;
    private final Clock clock;

    public SummaryService(TransactionRepository transactions, Clock clock) {
        this.transactions = transactions;
        this.clock = clock;
    }

    public SummaryResponse get(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = from == null ? today.withDayOfMonth(1) : from;
        LocalDate end = to == null ? today : to;
        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to.");
        }
        long income = 0;
        long expenses = 0;
        long transfers = 0;
        Map<SpendingCategory, Long> categoryTotals = new EnumMap<>(SpendingCategory.class);
        for (var transaction : transactions.findInRange(start, end)) {
            switch (transaction.getKind()) {
                case INCOME -> income = Math.addExact(income, transaction.getAmountSen());
                case EXPENSE -> {
                    expenses = Math.addExact(expenses, transaction.getAmountSen());
                    categoryTotals.merge(transaction.getCategory(), transaction.getAmountSen(), Math::addExact);
                }
                case TRANSFER -> transfers = Math.addExact(transfers, transaction.getAmountSen());
            }
        }
        Map<SpendingCategory, BigDecimal> categories = new EnumMap<>(SpendingCategory.class);
        categoryTotals.forEach((category, amount) -> categories.put(category, Money.toMyr(amount)));
        return new SummaryResponse(start, end, Money.toMyr(income), Money.toMyr(expenses),
                Money.toMyr(Math.subtractExact(income, expenses)), Money.toMyr(transfers), categories);
    }
}
