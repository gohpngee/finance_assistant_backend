"""Run against an empty demo database: python3 scripts/demo.py http://127.0.0.1:8081"""

import json
import sys
from datetime import datetime
from decimal import Decimal
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from zoneinfo import ZoneInfo


BASE_URL = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8081").rstrip("/")
TODAY = datetime.now(ZoneInfo("Asia/Kuala_Lumpur")).date().isoformat()


def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    request = Request(BASE_URL + path, data=data, method=method,
                      headers={"Content-Type": "application/json"})
    with urlopen(request, timeout=10) as response:
        return json.load(response)


def create_account(name, account_type, opening_balance):
    return call("POST", "/api/accounts", {
        "name": name,
        "type": account_type,
        "openingBalance": opening_balance,
        "openingDate": TODAY,
    })["id"]


def record(kind, amount, source, description, key, destination=None, category=None):
    body = {
        "kind": kind,
        "amount": amount,
        "transactionDate": TODAY,
        "description": description,
        "sourceAccountId": source,
        "sourceMessageKey": key,
    }
    if destination is not None:
        body["destinationAccountId"] = destination
    if category is not None:
        body["category"] = category
    return call("POST", "/api/transactions", body)


def main():
    call("GET", "/health")
    if call("GET", "/api/accounts"):
        raise SystemExit("Demo requires an empty database. Start the app with a separate new FINANCE_DB_PATH.")

    bank = create_account("Demo bank", "BANK_ACCOUNT", "1000.00")
    amex = create_account("Demo Amex", "CREDIT_CARD", "0.00")
    paylater = create_account("Demo SPayLater", "PAYLATER", "0.00")
    purchase = record("EXPENSE", "300.00", paylater, "Shoes via SPayLater",
                      "demo:purchase", category="SHOPPING")
    record("TRANSFER", "60.00", amex, "Partial SPayLater repayment",
           "demo:paylater-payment", destination=paylater)
    record("TRANSFER", "60.00", bank, "Pay Amex bill", "demo:amex-payment", destination=amex)

    balances = {item["id"]: Decimal(str(item["balance"])) for item in call("GET", "/api/accounts")}
    summary = call("GET", "/api/summary")
    expected_balances = {bank: Decimal("940.00"), amex: Decimal("0.00"), paylater: Decimal("240.00")}
    if balances != expected_balances or Decimal(str(summary["expenses"])) != Decimal("300.00"):
        raise SystemExit("Demo produced unexpected totals: " + json.dumps(summary))

    print(json.dumps({
        "result": "Demo passed",
        "bankBalance": str(balances[bank]),
        "amexDebt": str(balances[amex]),
        "spaylaterDebt": str(balances[paylater]),
        "spending": str(summary["expenses"]),
        "transfers": str(summary["transfers"]),
        "accountIds": {"bank": bank, "amex": amex, "spaylater": paylater},
        "purchaseId": purchase["id"],
    }, indent=2))


if __name__ == "__main__":
    try:
        main()
    except HTTPError as error:
        raise SystemExit(f"HTTP {error.code}: {error.read().decode()}") from error
    except URLError as error:
        raise SystemExit(f"Cannot reach {BASE_URL}: {error.reason}. Start the backend first.") from error
