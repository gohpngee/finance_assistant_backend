package com.finance_assistant.transaction;

import com.finance_assistant.account.Account;
import com.finance_assistant.transaction.enums.SpendingCategory;
import com.finance_assistant.transaction.enums.TransactionKind;
import jakarta.persistence.Column;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "transactions", check = @CheckConstraint(
        name = "ck_transactions_positive_amount", constraint = "amount_sen > 0"), indexes = {
        @Index(name = "idx_transactions_date", columnList = "transaction_date"),
        @Index(name = "idx_transactions_source", columnList = "source_account_id"),
        @Index(name = "idx_transactions_destination", columnList = "destination_account_id")
})
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INTEGER")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionKind kind;

    @Column(name = "amount_sen", nullable = false)
    private long amountSen;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false, length = 250)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SpendingCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id")
    private Account destinationAccount;

    @Column(name = "source_message_key", unique = true, length = 200, updatable = false)
    private String sourceMessageKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Transaction() {
    }

    public Transaction(TransactionKind kind, long amountSen, LocalDate transactionDate,
                       String description, SpendingCategory category, Account sourceAccount,
                       Account destinationAccount, String sourceMessageKey, Instant createdAt) {
        updateDetails(kind, amountSen, transactionDate, description, category,
                sourceAccount, destinationAccount);
        this.sourceMessageKey = sourceMessageKey;
        this.createdAt = createdAt;
    }

    public void updateDetails(TransactionKind kind, long amountSen, LocalDate transactionDate,
                              String description, SpendingCategory category,
                              Account sourceAccount, Account destinationAccount) {
        this.kind = kind;
        this.amountSen = amountSen;
        this.transactionDate = transactionDate;
        this.description = description;
        this.category = category;
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
    }

    public Long getId() { return id; }
    public TransactionKind getKind() { return kind; }
    public long getAmountSen() { return amountSen; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public String getDescription() { return description; }
    public SpendingCategory getCategory() { return category; }
    public Account getSourceAccount() { return sourceAccount; }
    public Account getDestinationAccount() { return destinationAccount; }
    public String getSourceMessageKey() { return sourceMessageKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void markDeleted(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
