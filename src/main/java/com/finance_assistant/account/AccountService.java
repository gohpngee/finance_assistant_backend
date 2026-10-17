package com.finance_assistant.account;

import com.finance_assistant.common.Money;
import com.finance_assistant.transaction.TransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AccountService {
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final Clock clock;

    public AccountService(AccountRepository accounts, TransactionRepository transactions, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Transactional
    public AccountResponse create(AccountRequest request) {
        String name = request.name().strip();
        if (accounts.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this name already exists.");
        }
        if (request.openingDate().isAfter(LocalDate.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Opening date cannot be in the future.");
        }
        Account account = new Account(name, request.type(), Money.toSen(request.openingBalance()),
                request.openingDate(), Instant.now(clock));
        return toResponse(accounts.saveAndFlush(account));
    }

    public List<AccountResponse> list() {
        return accounts.findAllByOrderByIdAsc().stream().map(this::toResponse).toList();
    }

    public AccountResponse get(long id) {
        return toResponse(accounts.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Account " + id + " was not found.")));
    }

    private AccountResponse toResponse(Account account) {
        LocalDate today = LocalDate.now(clock);
        long assetChange = transactions.sumAssetChange(account.getId(), today);
        long balanceChange = account.getType().isDebt() ? -assetChange : assetChange;
        long balance = Math.addExact(account.getOpeningBalanceSen(), balanceChange);
        return new AccountResponse(account.getId(), account.getName(), account.getType(),
                Money.toMyr(account.getOpeningBalanceSen()), account.getOpeningDate(),
                Money.toMyr(balance), today, account.isActive(), account.getCreatedAt());
    }
}
