# creditas-api-spring-boot-starter

Spring Boot auto-configuration for the client: add the dependency, set `creditas.*`, inject `CreditasClient`.
Compiled against Spring Boot 3.5; CI runs its tests on 3.5.16 and 4.1.2.

## Configure

```yaml
creditas:
  key: ${CREDITAS_KEY}
  account-id: ${CREDITAS_ACCOUNT_ID}
```

That is the whole setup for an application reading one account: inject `CreditasClient`, `ApiKey` and
`CreditasProperties` for the account id. One serving many account holders leaves `creditas.key` out of its
configuration entirely and passes each one's key with the call: an empty value, as `${CREDITAS_KEY:}` gives with the
variable unset, still counts as set and stops the application. Keep the key in the environment, never in a committed
file.

A job that marks invoices paid:

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

`@EnableScheduling` on a configuration class turns it on.

| Property | Default |
|---|---|
| `creditas.key` | absent: no `ApiKey` bean; empty stops the application |
| `creditas.account-id` | unset |
| `creditas.base-uri` | `https://api.creditas.cz/oam/` |
| `creditas.request-timeout` | `30s` |

A `CreditasClient` bean of your own replaces the one built here. A key with nothing in it once spaces are dropped,
or a base URI that is not an absolute http(s) URI, stops the application at startup.
