# Finance assistant backend MVP

A local Spring Boot + JPA + SQLite API for your personal MYR ledger. The working model has **two entities**, `Account` and `Transaction`, and three transaction kinds: `INCOME`, `EXPENSE`, and `TRANSFER`.

## Run it

You need Java 21 or newer and Maven. From this project directory:

```sh
mvn spring-boot:run
```

The API listens on `http://127.0.0.1:8080`. Check it from another terminal:

```sh
curl http://127.0.0.1:8080/health
```

The app creates `finance.db` in this directory and keeps records across restarts. The database and Maven's `target/` output are ignored by Git. All business dates use `Asia/Kuala_Lumpur`.

Use `FINANCE_DB_PATH` to choose a different SQLite file and `PORT` to choose another port. The database's parent directory must exist.

## See the purchase and repayment example

Start a separate empty demo database:

```sh
FINANCE_DB_PATH=./demo.db PORT=8081 mvn spring-boot:run
```

In another terminal, run:

```sh
python3 scripts/demo.py http://127.0.0.1:8081
```

The demo refuses to write if the database already contains accounts. It creates a bank account with RM1,000 and zero-debt Amex/SPayLater accounts, records a RM300 PayLater expense, repays RM60 with Amex, then pays that RM60 Amex debt from the bank.

Expected results: bank balance RM940, Amex debt RM0, SPayLater debt RM240, spending RM300, transfers RM120. The demo prints the actual results and account IDs. Inspect them with `GET /api/accounts` or `GET /api/summary`.

## Create your accounts

Example request (use your actual opening amount and start date):

```sh
curl -X POST http://127.0.0.1:8080/api/accounts \
  -H 'Content-Type: application/json' \
  -d '{"name":"Maybank savings","type":"BANK_ACCOUNT","openingBalance":"1000.00","openingDate":"2026-10-17"}'
```

Account types are `BANK_ACCOUNT`, `CREDIT_CARD`, and `PAYLATER`. Debit-card purchases use their bank account. Create separate credit-card rows for UOB and Amex.

Account names must be unique. The opening amount is the balance just before new recorded events on `openingDate`; do not also enter older movements already included in that amount. A positive card/PayLater balance means debt owed. A negative card balance represents account credit; a negative bank balance represents an overdraft. Opening balances contribute no income or spending.

Responses include the opening balance and a calculated current `balance`, in MYR. A transaction date cannot precede either involved account's opening date or be in the future.

## Record transactions

All API money values are **MYR**, with at most two decimal places. You can send `"12.50"` or `12.50`. The Java request uses `BigDecimal`; persistence uses integer sen to keep SQLite amounts exact. Positive amounts are required; transaction kind and account type determine the effect.

- `EXPENSE`: requires `sourceAccountId`; omit `destinationAccountId`. Purchases, fees, and money sent to another person are expenses. A bank expense reduces available money; a card/PayLater expense increases debt.
- `INCOME`: requires `destinationAccountId`; omit `sourceAccountId`.
- `TRANSFER`: requires two different account IDs. A credit-card bill payment or PayLater repayment is a transfer, excluded from spending.

Example purchase; replace `3` with the SPayLater account ID returned when you create it:

```sh
curl -X POST http://127.0.0.1:8080/api/transactions \
  -H 'Content-Type: application/json' \
  -d '{"kind":"EXPENSE","amount":"300.00","transactionDate":"2026-10-17","description":"Shoes via SPayLater","category":"SHOPPING","sourceAccountId":3,"sourceMessageKey":"manual:purchase:1"}'
```

Example partial repayment, assuming Amex ID `2` and SPayLater ID `3`:

```sh
curl -X POST http://127.0.0.1:8080/api/transactions \
  -H 'Content-Type: application/json' \
  -d '{"kind":"TRANSFER","amount":"60.00","transactionDate":"2026-10-17","description":"Repay SPayLater with Amex","sourceAccountId":2,"destinationAccountId":3,"sourceMessageKey":"manual:repayment:1"}'
```

This leaves RM240 SPayLater debt and adds RM60 Amex debt. The original RM300 remains the only spending from this example. Paying Amex from the bank is another transfer.

Expense categories: `DINING`, `GROCERIES`, `FUEL`, `TRANSPORT`, `GRAB`, `SHOPPING`, `BILLS`, `FEES`, and `OTHER`. Omitted expense categories become `OTHER`. Income and transfer requests must omit the category. These are reporting labels, not issuer reward classifications.

`sourceMessageKey` is optional. Repeating an identical request with the same key returns the existing record and ID, without changing totals. Reusing that key with different details returns HTTP 409. Future Telegram integration can use a stable chat/message key here.

## Endpoints

- `GET /health`: application status.
- `POST /api/accounts`: create an account with its opening balance.
- `GET /api/accounts`: list accounts with calculated balances.
- `GET /api/accounts/{id}`: read one account and its balance.
- `POST /api/transactions`: record income, expense, or transfer.
- `GET /api/transactions/{id}`: read a transaction.
- `GET /api/transactions`: list transactions, newest date/ID first. Optional filters: `from`, `to`, `kind`, `accountId`; pagination: `page=0&size=50` (maximum size 200).
- `PUT /api/transactions/{id}`: correct an entry using the same request shape as POST. `sourceMessageKey` can be omitted but cannot be changed.
- `DELETE /api/transactions/{id}`: remove an entry from balances and reports. Internally it is marked deleted, retaining its source key so replay cannot recreate it; such a replay returns HTTP 410.
- `GET /api/summary?from=2026-10-01&to=2026-10-31`: income, expenses, net, transfer volume, and expenses by category. Dates are inclusive; omitted summary dates default to the current month's first day and today.

```sh
curl http://127.0.0.1:8080/api/accounts
curl 'http://127.0.0.1:8080/api/transactions?kind=EXPENSE&page=0&size=20'
curl 'http://127.0.0.1:8080/api/summary?from=2026-10-01&to=2026-10-31'
```

`net` means recorded income minus expenses; it is not the change in one bank account. `transfers` is total transfer volume, so moving the same debt twice appears twice in that figure without adding spending.

Invalid fields or account combinations return HTTP 400; missing records return 404; conflicting names/source keys return 409. Errors use a JSON problem response with a `detail` field, and field-validation responses include `errors`.

## Follow the code

Start with `account/Account.java` and `transaction/Transaction.java`. Then read the corresponding controllers, services, and repositories:

```text
JSON request → Controller → Request DTO → Service → Repository → JPA entity / SQLite
                                       ↓
                                Response DTO → JSON
```

The service validates the event. Account balances are derived from the opening amount plus transaction history. A source bank loses money; a source card/PayLater gains debt. A destination bank gains money; a destination card/PayLater has debt reduced. Corrections and deletions automatically change the derived balance; there is no second current-debt field to update.

`SummaryService` calculates reporting totals and has no database entity of its own. Explicit constructors and getters are used so the entity behavior is visible while learning.

`schema.sql` creates the initial tables, foreign keys, checks, and indexes. Hibernate validates that schema against your entities. The SQL is needed because Hibernate's SQLite table generation does not create these account foreign keys; repository operations still use JPA.

## Tests

```sh
mvn test
mvn package
```

Integration tests start an actual HTTP server with a temporary SQLite database and a fixed Malaysia clock. They cover purchase/repayment balances, income and person payments, exact decimal amounts, retries, invalid entries, corrections/deletion, date boundaries, pagination, and opening balances. Test data never uses your `finance.db`.

## Next features

The MVP tracks total debt, including partial repayments. Per-purchase 1/3/6-month installment schedules and allocations, cashback recommendations, Telegram/OpenClaw wiring, and scheduled reports are subsequent steps. The earlier five-entity design in `LEDGER_RULES.md` describes that later installment phase.

For this first model, `schema.sql` creates missing tables and Hibernate validates them. Editing that script does not migrate existing tables. Before evolving a database containing valuable history, back it up and introduce versioned migrations.
