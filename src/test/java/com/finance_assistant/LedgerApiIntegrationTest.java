package com.finance_assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finance_assistant.account.AccountRepository;
import com.finance_assistant.transaction.TransactionRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {FinanceAssistantApplication.class, LedgerApiIntegrationTest.FixedClockConfiguration.class})
class LedgerApiIntegrationTest {
    private static final Path DATABASE = temporaryDatabase();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private TransactionRepository transactions;
    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
    }

    @BeforeEach
    void clearDatabase() {
        transactions.deleteAllInBatch();
        accounts.deleteAllInBatch();
    }

    @Test
    void payLaterRepaymentsMoveDebtWithoutIncreasingSpending() throws Exception {
        long bank = account("Bank", "BANK_ACCOUNT", "1000.00");
        long amex = account("Amex", "CREDIT_CARD", "0.00");
        long payLater = account("SPayLater", "PAYLATER", "0.00");
        var purchase = transaction("EXPENSE", "300.00", payLater, null, "Shoes");
        purchase.put("category", "SHOPPING");
        request("POST", "/api/transactions", purchase, 201);
        request("POST", "/api/transactions",
                transaction("TRANSFER", "60.00", amex, payLater, "Partial SPayLater repayment"), 201);
        assertBalance(payLater, "240.00");
        assertBalance(amex, "60.00");
        assertBalance(bank, "1000.00");

        request("POST", "/api/transactions",
                transaction("TRANSFER", "60.00", bank, amex, "Pay Amex"), 201);
        assertBalance(bank, "940.00");
        assertBalance(amex, "0.00");
        JsonNode summary = request("GET", "/api/summary", null, 200);
        assertMoney(summary, "income", "0.00");
        assertMoney(summary, "expenses", "300.00");
        assertMoney(summary, "net", "-300.00");
        assertMoney(summary, "transfers", "120.00");
        assertMoney(summary.get("expensesByCategory"), "SHOPPING", "300.00");
        JsonNode filtered = request("GET", "/api/transactions?kind=TRANSFER&accountId=" + payLater, null, 200);
        assertThat(filtered.get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void incomeAndOrdinaryExpensesUpdateBankBalances() throws Exception {
        long bank = account("Savings", "BANK_ACCOUNT", "1000.00");
        long secondBank = account("Second bank", "BANK_ACCOUNT", "50.00");
        request("POST", "/api/transactions",
                transaction("INCOME", "500.00", null, bank, "Salary"), 201);
        request("POST", "/api/transactions",
                transaction("EXPENSE", "40.00", bank, null, "Sent to a friend"), 201);
        request("POST", "/api/transactions",
                transaction("TRANSFER", "200.00", bank, secondBank, "Move money"), 201);
        assertBalance(bank, "1260.00");
        assertBalance(secondBank, "250.00");
        JsonNode summary = request("GET", "/api/summary", null, 200);
        assertMoney(summary, "income", "500.00");
        assertMoney(summary, "expenses", "40.00");
        assertMoney(summary, "net", "460.00");
        assertMoney(summary.get("expensesByCategory"), "OTHER", "40.00");
    }

    @Test
    void decimalAmountsRemainExactThroughSqlite() throws Exception {
        long bank = account("Small amounts", "BANK_ACCOUNT", "1.00");
        request("POST", "/api/transactions",
                transaction("EXPENSE", "0.10", bank, null, "First"), 201);
        request("POST", "/api/transactions",
                transaction("EXPENSE", "0.20", bank, null, "Second"), 201);
        assertBalance(bank, "0.70");
        assertMoney(request("GET", "/api/summary", null, 200), "expenses", "0.30");
    }

    @Test
    void retryingASourceMessageDoesNotCreateAnotherTransaction() throws Exception {
        long bank = account("Retry account", "BANK_ACCOUNT", "10.00");
        var body = transaction("EXPENSE", "0.50", bank, null, "Tea");
        body.put("sourceMessageKey", "telegram:123:456");
        JsonNode first = request("POST", "/api/transactions", body, 201);
        JsonNode retry = request("POST", "/api/transactions", body, 201);
        assertThat(retry.get("id").asLong()).isEqualTo(first.get("id").asLong());
        assertThat(transactions.count()).isEqualTo(1);
        assertBalance(bank, "9.50");
        body.put("amount", "1.00");
        request("POST", "/api/transactions", body, 409);
        assertThat(transactions.count()).isEqualTo(1);
        assertBalance(bank, "9.50");
    }

    @Test
    void invalidTransactionsDoNotChangeTheLedger() throws Exception {
        long bank = account("Validation", "BANK_ACCOUNT", "100.00");
        request("POST", "/api/transactions",
                transaction("EXPENSE", "1.00", null, null, "Missing source"), 400);
        request("POST", "/api/transactions",
                transaction("TRANSFER", "1.00", bank, bank, "Same account"), 400);
        request("POST", "/api/transactions",
                transaction("INCOME", "1.00", bank, bank, "Invalid income"), 400);
        request("POST", "/api/transactions",
                transaction("EXPENSE", "1.001", bank, null, "Too precise"), 400);
        request("POST", "/api/transactions",
                transaction("EXPENSE", "-1.00", bank, null, "Negative"), 400);
        var early = transaction("EXPENSE", "1.00", bank, null, "Before opening");
        early.put("transactionDate", "2026-09-30");
        request("POST", "/api/transactions", early, 400);
        early.put("transactionDate", "2026-10-18");
        request("POST", "/api/transactions", early, 400);
        request("POST", "/api/transactions",
                transaction("EXPENSE", "1.00", 999999L, null, "Unknown account"), 404);
        request("POST", "/api/transactions",
                transaction("PURCHASE", "1.00", bank, null, "Invalid enum"), 400);
        var unknownField = transaction("EXPENSE", "1.00", bank, null, "Typo");
        unknownField.put("destnationAccountId", bank);
        request("POST", "/api/transactions", unknownField, 400);
        assertThat(transactions.count()).isZero();
        assertBalance(bank, "100.00");
    }

    @Test
    void correctionsAndDeletionRecalculateBalancesAndTotals() throws Exception {
        long bank = account("Corrections", "BANK_ACCOUNT", "1000.00");
        var original = transaction("EXPENSE", "100.00", bank, null, "Wrong amount");
        original.put("sourceMessageKey", "telegram:123:789");
        long id = request("POST", "/api/transactions", original, 201).get("id").asLong();
        var correction = transaction("EXPENSE", "40.00", bank, null, "Corrected amount");
        JsonNode updated = request("PUT", "/api/transactions/" + id, correction, 200);
        assertThat(updated.get("sourceMessageKey").asText()).isEqualTo("telegram:123:789");
        assertBalance(bank, "960.00");
        assertMoney(request("GET", "/api/summary", null, 200), "expenses", "40.00");
        request("DELETE", "/api/transactions/" + id, null, 204);
        request("GET", "/api/transactions/" + id, null, 404);
        assertBalance(bank, "1000.00");
        assertMoney(request("GET", "/api/summary", null, 200), "expenses", "0.00");
        assertThat(request("GET", "/api/transactions", null, 200).get("totalElements").asLong()).isZero();
        request("POST", "/api/transactions", original, 410);
        assertBalance(bank, "1000.00");
    }

    @Test
    void reportsUseInclusiveDatesAndListsUseStablePagination() throws Exception {
        long bank = account("Dates", "BANK_ACCOUNT", "10.00");
        var first = transaction("EXPENSE", "1.00", bank, null, "First day");
        first.put("transactionDate", "2026-10-01");
        request("POST", "/api/transactions", first, 201);
        var second = transaction("EXPENSE", "2.00", bank, null, "Second day");
        second.put("transactionDate", "2026-10-02");
        request("POST", "/api/transactions", second, 201);
        var third = transaction("INCOME", "5.00", null, bank, "Third day");
        third.put("transactionDate", "2026-10-03");
        request("POST", "/api/transactions", third, 201);
        JsonNode summary = request("GET", "/api/summary?from=2026-10-01&to=2026-10-02", null, 200);
        assertMoney(summary, "income", "0.00");
        assertMoney(summary, "expenses", "3.00");
        assertMoney(request("GET", "/api/summary?from=2026-10-04&to=2026-10-05", null, 200), "expenses", "0.00");
        JsonNode page = request("GET", "/api/transactions?page=1&size=1", null, 200);
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page.get("totalPages").asInt()).isEqualTo(3);
        assertThat(page.get("transactions").get(0).get("description").asText()).isEqualTo("Second day");
        JsonNode boundary = request("GET", "/api/transactions?from=2026-10-02&to=2026-10-02", null, 200);
        assertThat(boundary.get("totalElements").asLong()).isEqualTo(1);
        request("GET", "/api/transactions?size=0", null, 400);
        request("GET", "/api/summary?from=2026-10-02&to=2026-10-01", null, 400);
        request("GET", "/api/transactions?from=invalid", null, 400);
    }

    @Test
    void openingBalancesAreNotNewIncomeOrSpending() throws Exception {
        long bank = account("Opening bank", "BANK_ACCOUNT", "-10.00");
        long card = account("Opening card credit", "CREDIT_CARD", "-5.00");
        long debt = account("Opening PayLater debt", "PAYLATER", "100.00");
        assertBalance(bank, "-10.00");
        assertBalance(card, "-5.00");
        assertBalance(debt, "100.00");
        JsonNode summary = request("GET", "/api/summary", null, 200);
        assertMoney(summary, "income", "0.00");
        assertMoney(summary, "expenses", "0.00");
        request("POST", "/api/accounts", Map.of("name", "opening BANK", "type", "BANK_ACCOUNT",
                "openingBalance", "0.00", "openingDate", "2026-10-01"), 409);
    }

    @Test
    void sqliteEnforcesAccountForeignKeys() {
        assertThat(jdbc.queryForObject("PRAGMA foreign_keys", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("PRAGMA foreign_key_list(transactions)")).hasSize(2);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transactions
                    (kind, amount_sen, transaction_date, description, category, source_account_id, created_at)
                VALUES ('EXPENSE', 100, '2026-10-17', 'Orphan', 'OTHER', 999999, '2026-10-17 00:00:00')
                """)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("FOREIGN KEY constraint failed");
    }

    private long account(String name, String type, String openingBalance) throws Exception {
        return request("POST", "/api/accounts", Map.of("name", name, "type", type,
                "openingBalance", openingBalance, "openingDate", "2026-10-01"), 201).get("id").asLong();
    }

    private Map<String, Object> transaction(String kind, String amount, Long source, Long destination,
                                            String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", kind);
        body.put("amount", amount);
        body.put("transactionDate", "2026-10-17");
        body.put("description", description);
        if (source != null) body.put("sourceAccountId", source);
        if (destination != null) body.put("destinationAccountId", destination);
        return body;
    }

    private void assertBalance(long id, String expected) throws Exception {
        assertMoney(request("GET", "/api/accounts/" + id, null, 200), "balance", expected);
    }

    private void assertMoney(JsonNode node, String field, String expected) {
        assertThat(new BigDecimal(node.get(field).asText())).isEqualByComparingTo(expected);
    }

    private JsonNode request(String method, String path, Object body, int expectedStatus) throws Exception {
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .method(method, publisher).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s: %s", method, path, response.body()).isEqualTo(expectedStatus);
        return response.body().isBlank() ? null : json.readTree(response.body());
    }

    private static Path temporaryDatabase() {
        try {
            Path directory = Files.createTempDirectory("finance-backend-test-");
            Path database = directory.resolve("ledger.db");
            directory.toFile().deleteOnExit();
            database.toFile().deleteOnExit();
            return database;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-10-17T04:00:00Z"), ZoneId.of("Asia/Kuala_Lumpur"));
        }
    }
}
