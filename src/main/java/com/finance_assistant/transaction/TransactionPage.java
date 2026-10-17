package com.finance_assistant.transaction;

import java.util.List;

public record TransactionPage(
        List<TransactionResponse> transactions, int page, int size,
        long totalElements, int totalPages) {
}
