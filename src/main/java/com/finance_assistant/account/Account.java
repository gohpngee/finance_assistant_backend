package com.finance_assistant.account;

import com.finance_assistant.account.enums.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INTEGER")
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type;

    @Column(name = "opening_balance_sen", nullable = false)
    private long openingBalanceSen;

    @Column(name = "opening_date", nullable = false)
    private LocalDate openingDate;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
    }

    public Account(String name, AccountType type, long openingBalanceSen,
                   LocalDate openingDate, Instant createdAt) {
        this.name = name;
        this.type = type;
        this.openingBalanceSen = openingBalanceSen;
        this.openingDate = openingDate;
        this.active = true;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public AccountType getType() { return type; }
    public long getOpeningBalanceSen() { return openingBalanceSen; }
    public LocalDate getOpeningDate() { return openingDate; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
