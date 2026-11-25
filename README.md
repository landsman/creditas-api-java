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

### In Spring Boot

Add `cz.insuit:creditas-api-spring-boot-starter` instead of the client (not published yet:
[#10](https://github.com/landsman/creditas-api-java/issues/10)), and set the key and the account from the
environment:

```yaml
creditas:
  key: ${CREDITAS_KEY}
  account-id: ${CREDITAS_ACCOUNT_ID}
```

The starter gives a `CreditasClient`, an `ApiKey` and `CreditasProperties` as beans. A job that marks invoices paid:

```java
@Component
class IncomingPayments {
    private final CreditasClient creditas;
    private final ApiKey key;
    private final String accountId;
    private final Invoices invoices; // your own

    IncomingPayments(CreditasClient creditas, ApiKey key, CreditasProperties properties, Invoices invoices) {
        this.creditas = creditas;
        this.key = key;
        this.accountId = Objects.requireNonNull(properties.accountId(), "creditas.account-id");
        this.invoices = invoices;
    }

    // Every 10 minutes, at second 17. Three days back, since a payment is dated by its value date, not when it arrived.
    @Scheduled(cron = "17 */10 * * * *")
    void match() {
        var today = LocalDate.now(ZoneId.of("Europe/Prague"));
        try {
            for (var t : creditas.transactions(key, accountId, today.minusDays(3), today)) {
                var invoice = t.symbols().variable();
                if (t.direction() == Transaction.Direction.CREDIT && invoice != null) {
                    // Keyed by t.id() under a unique constraint: the same payment is read on every run, and on
                    // every instance of the app.
                    invoices.markPaid(t.id(), invoice, t.amount());
                }
            }
        } catch (CreditasException.InvalidKey e) {
            // Expired or revoked: only the account holder can issue a new key. Tell them, do not retry.
            invoices.keyNeedsRenewal();
        }
        // Failed and NoAnswer pass: the next run tries again.
    }
}
```

`@EnableScheduling` on a configuration class turns the job on. An application for many account holders leaves
`creditas.key` out and passes each one's `ApiKey` with the call; the [starter's README](modules/creditas-api-spring-boot-starter/README.md)
has the properties and what stops the application at startup.

### Good to know

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
| [Spring Boot starter](modules/creditas-api-spring-boot-starter/README.md) | `creditas.*` in `application.yml`, the client and the key as beans |

Building it: `make build` (format check, Error Prone with NullAway, tests, declared dependencies); `make` lists the
rest.

Licence: [Apache 2.0](LICENSE)
