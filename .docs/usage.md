# Using the library

The same jar from Java, Kotlin and Spring Boot. The key and the account id come from the key's detail in internet
banking; see [setting it up](creditas-api.md#setting-it-up).

## Spring Boot, in Kotlin

With the [starter](../modules/creditas-api-spring-boot-starter/README.md) (not published yet:
[#10](https://github.com/landsman/creditas-api-java/issues/10)), the key and the account come from the environment:

```yaml
creditas:
  key: ${CREDITAS_KEY}
  account-id: ${CREDITAS_ACCOUNT_ID}
```

A job that marks invoices paid, with `@EnableScheduling` on a configuration class. Every 10 minutes, three days
back, since a payment is dated by its value date, not when it arrived:

```kotlin
@Component
class IncomingPayments(
    private val creditas: CreditasClient,
    private val key: ApiKey,
    properties: CreditasProperties,
    private val invoices: Invoices, // your own
) {
    private val accountId = requireNotNull(properties.accountId) { "creditas.account-id" }

    @Scheduled(cron = "17 */10 * * * *")
    fun match() {
        val today = LocalDate.now(ZoneId.of("Europe/Prague"))
        try {
            creditas.transactions(key, accountId, today.minusDays(3), today)
                .filter { it.direction == Transaction.Direction.CREDIT }
                .forEach { t -> t.symbols.variable?.let { invoices.markPaid(t.id, it, t.amount) } }
        } catch (e: CreditasException.InvalidKey) {
            invoices.keyNeedsRenewal()
        }
    }
}
```

Deduplicate by `t.id` under a unique constraint: the same payment is read on every run, and on every instance of
the app. An `InvalidKey` means only the account holder can issue a new key, so tell them rather than retry;
`Failed` and `NoAnswer` pass, and the next run tries again.

## Other ways in

<details><summary>Spring Boot, in Java</summary>

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

</details>

<details><summary>Kotlin, without Spring</summary>

The JSpecify annotations reach Kotlin, so a field Creditas may leave out is a `String?`.

```kotlin
CreditasClient().use { creditas ->
    val key = ApiKey(System.getenv("CREDITAS_KEY"))
    val accountId = System.getenv("CREDITAS_ACCOUNT_ID")

    val balance = creditas.balance(key, accountId)
    creditas.transactions(key, accountId, LocalDate.now().minusDays(7), LocalDate.now())
        .filter { it.direction == Transaction.Direction.CREDIT }
        .forEach { t ->
            val invoice = t.symbols.variable ?: return@forEach
            // mark invoice paid with t.amount
        }
}
```

</details>

<details><summary>Java, without Spring</summary>

```java
try (var creditas = new CreditasClient()) {
    var key = new ApiKey(System.getenv("CREDITAS_KEY"));
    var accountId = System.getenv("CREDITAS_ACCOUNT_ID");

    var balance = creditas.balance(key, accountId);
    for (var t : creditas.transactions(key, accountId, LocalDate.now().minusDays(7), LocalDate.now())) {
        if (t.direction() == Transaction.Direction.CREDIT && "2026017".equals(t.symbols().variable())) {
            // mark invoice 2026017 paid with t.amount()
        }
    }
}
```

</details>

## Good to know

- **One key opens one account**, and the key carries the rights of the person who issued it.
- **Every failure is a `CreditasException`**, one subtype per thing to do about it: `InvalidKey` (ask the account
  holder for a new key), `Rejected` (the request itself is wrong), `Failed` (try later), `NoAnswer`,
  `InvalidResponse`. Creditas answers every error with HTTP 500, so the subtype comes from the error's name.
- **A transaction has a date, not a time.** Deduplicate by `Transaction.id()`.
- **`toString()` leaks nothing**: not the key, not a counterparty's name or account, not the message.
