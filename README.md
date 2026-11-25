# creditas-api-java

**Read a Banka CREDITAS account from Java: its balance and every payment, with the payer's name, all three symbols
and the message.** No contract with the bank and no PSD2 licence: the account holder issues a key in internet banking
and hands it to your application.

For Java 21+ and Kotlin, one jar, no framework required.

**Status: not tried against a live account yet.** The client follows the bank's public specification and its PDF;
the tests run on synthetic answers. What a live account has to settle is under [open questions](.docs/creditas-api.md#open-questions).

## Using it

```java
try (var creditas = new CreditasClient()) {
    var key = new ApiKey(System.getenv("CREDITAS_KEY"));
    var accountId = "ded30c093bbe9c2d6e8735c8b4a7efc8a4bfbcc4"; // the key's detail in internet banking

    var balance = creditas.balance(key, accountId);
    for (var t : creditas.transactions(key, accountId, LocalDate.now().minusDays(7), LocalDate.now())) {
        if (t.direction() == Transaction.Direction.CREDIT && "2026017".equals(t.symbols().variable())) {
            // mark invoice 2026017 paid with t.amount()
        }
    }
}
```

- **One key opens one account**, and the key carries the rights of the person who issued it; see
  [setting it up](.docs/creditas-api.md#setting-it-up).
- **Every failure is a `CreditasException`**, one subtype per thing to do about it: `InvalidKey` (ask the account
  holder for a new key), `Rejected` (the request itself is wrong), `Failed` (try later), `NoAnswer`,
  `InvalidResponse`. Creditas answers every error with HTTP 500, so the subtype comes from the error's name.
- **A transaction has a date, not a time.** Deduplicate by `Transaction.id()`.
- **`toString()` leaks nothing**: not the key, not a counterparty's name or account, not the message.

## Docs

| Read | For |
|---|---|
| [Creditas API](.docs/creditas-api.md) | what the bank offers, the key, the endpoints, what is still unknown |

Building it: `make build` (format check, Error Prone with NullAway, tests, declared dependencies); `make` lists the
rest.

Licence: [Apache 2.0](LICENSE)
