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

A job that marks invoices paid, in Java and Kotlin: [Using the library](../../.docs/usage.md#spring-boot).

| Property | Default |
|---|---|
| `creditas.key` | absent: no `ApiKey` bean; empty stops the application |
| `creditas.account-id` | unset |
| `creditas.base-uri` | `https://api.creditas.cz/oam/` |
| `creditas.request-timeout` | `30s` |

A `CreditasClient` bean of your own replaces the one built here. A key with nothing in it once spaces are dropped,
or a base URI that is not an absolute http(s) URI, stops the application at startup.
