package com.finance_assistant.transaction;

import com.finance_assistant.account.Account;
import com.finance_assistant.account.AccountRepository;
import com.finance_assistant.common.Money;
import com.finance_assistant.transaction.enums.SpendingCategory;
import com.finance_assistant.transaction.enums.TransactionKind;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class TransactionService {
    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final Clock clock;

    public TransactionService(TransactionRepository transactions, AccountRepository accounts, Clock clock) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        SpendingCategory category = normalizeCategory(request);
        String key = normalizeKey(request.sourceMessageKey());
        if (key != null) {
            var existing = transactions.findBySourceMessageKey(key);
            if (existing.isPresent()) {
                if (existing.get().getDeletedAt() != null) {
                    throw new ResponseStatusException(HttpStatus.GONE,
                            "This source message belongs to a deleted transaction; it cannot be replayed.");
                }
                if (!matches(existing.get(), request, category)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "This sourceMessageKey was already used for a different transaction.");
                }
                return toResponse(existing.get());
            }
        }
        Accounts involved = validate(request);
        Transaction transaction = new Transaction(request.kind(), Money.toSen(request.amount()),
                request.transactionDate(), request.description().strip(), category,
                involved.source(), involved.destination(), key, Instant.now(clock));
        return toResponse(transactions.saveAndFlush(transaction));
    }

    public TransactionResponse get(long id) {
        return toResponse(find(id));
    }

    public TransactionPage list(LocalDate from, LocalDate to, TransactionKind kind,
                                Long accountId, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw badRequest("from must be on or before to.");
        }
        if (page < 0 || size < 1 || size > 200) {
            throw badRequest("page must be at least 0; size must be between 1 and 200.");
        }
        if (accountId != null && !accounts.existsById(accountId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account " + accountId + " was not found.");
        }
        Page<Transaction> result = transactions.search(from, to, kind, accountId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate", "id")));
        return new TransactionPage(result.getContent().stream().map(this::toResponse).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public TransactionResponse update(long id, TransactionRequest request) {
        Transaction transaction = find(id);
        String suppliedKey = normalizeKey(request.sourceMessageKey());
        if (suppliedKey != null && !suppliedKey.equals(transaction.getSourceMessageKey())) {
            throw badRequest("A transaction's sourceMessageKey cannot be changed.");
        }
        SpendingCategory category = normalizeCategory(request);
        Accounts involved = validate(request);
        transaction.updateDetails(request.kind(), Money.toSen(request.amount()), request.transactionDate(),
                request.description().strip(), category, involved.source(), involved.destination());
        return toResponse(transactions.saveAndFlush(transaction));
    }

    @Transactional
    public void delete(long id) {
        Transaction transaction = find(id);
        transaction.markDeleted(Instant.now(clock));
        transactions.saveAndFlush(transaction);
    }

    private Accounts validate(TransactionRequest request) {
        switch (request.kind()) {
            case EXPENSE -> {
                if (request.sourceAccountId() == null || request.destinationAccountId() != null) {
                    throw badRequest("EXPENSE requires sourceAccountId and no destinationAccountId.");
                }
            }
            case INCOME -> {
                if (request.sourceAccountId() != null || request.destinationAccountId() == null) {
                    throw badRequest("INCOME requires destinationAccountId and no sourceAccountId.");
                }
            }
            case TRANSFER -> {
                if (request.sourceAccountId() == null || request.destinationAccountId() == null
                        || request.sourceAccountId().equals(request.destinationAccountId())) {
                    throw badRequest("TRANSFER requires two different account IDs.");
                }
            }
        }
        if (request.transactionDate().isAfter(LocalDate.now(clock))) {
            throw badRequest("Transaction date cannot be in the future; record actual events only.");
        }
        Account source = loadAccount(request.sourceAccountId(), request.transactionDate());
        Account destination = loadAccount(request.destinationAccountId(), request.transactionDate());
        return new Accounts(source, destination);
    }

    private Account loadAccount(Long id, LocalDate date) {
        if (id == null) {
            return null;
        }
        Account account = accounts.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Account " + id + " was not found."));
        if (!account.isActive()) {
            throw badRequest("Account " + id + " is inactive.");
        }
        if (date.isBefore(account.getOpeningDate())) {
            throw badRequest("Transaction date cannot be before account " + id + "'s opening date.");
        }
        return account;
    }

    private SpendingCategory normalizeCategory(TransactionRequest request) {
        if (request.kind() == TransactionKind.EXPENSE) {
            return request.category() == null ? SpendingCategory.OTHER : request.category();
        }
        if (request.category() != null) {
            throw badRequest("Only EXPENSE transactions may have a spending category.");
        }
        return null;
    }

    private boolean matches(Transaction transaction, TransactionRequest request, SpendingCategory category) {
        return transaction.getKind() == request.kind()
                && transaction.getAmountSen() == Money.toSen(request.amount())
                && transaction.getTransactionDate().equals(request.transactionDate())
                && transaction.getDescription().equals(request.description().strip())
                && transaction.getCategory() == category
                && Objects.equals(accountId(transaction.getSourceAccount()), request.sourceAccountId())
                && Objects.equals(accountId(transaction.getDestinationAccount()), request.destinationAccountId());
    }

    private String normalizeKey(String key) {
        return key == null || key.isBlank() ? null : key.strip();
    }

    private Transaction find(long id) {
        return transactions.findById(id).filter(transaction -> transaction.getDeletedAt() == null).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction " + id + " was not found."));
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(transaction.getId(), transaction.getKind(),
                Money.toMyr(transaction.getAmountSen()), transaction.getTransactionDate(),
                transaction.getDescription(), transaction.getCategory(),
                accountId(transaction.getSourceAccount()), accountId(transaction.getDestinationAccount()),
                transaction.getSourceMessageKey(), transaction.getCreatedAt());
    }

    private Long accountId(Account account) {
        return account == null ? null : account.getId();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record Accounts(Account source, Account destination) {
    }
}
