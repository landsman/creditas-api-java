# creditas-api-java

**Read a Banka CREDITAS account from Java: its balance and every payment, with the payer's name, all three symbols
and the message.** No contract with the bank and no PSD2 licence: the account holder issues a key in internet banking
and hands it to your application. For Java 21+ and Kotlin; a Spring Boot starter sets it up from `application.yml`.

**Status: not tried against a live account yet**, so not published either; see the
[open questions](https://github.com/landsman/creditas-api-java/issues).

```java
try (var creditas = new CreditasClient()) {
    var payments = creditas.transactions(new ApiKey(key), accountId, LocalDate.now().minusDays(7), LocalDate.now());
}
```

| Read | For |
|---|---|
| [Using the library](.docs/usage.md) | reading payments, the failures, what a transaction holds |
| [Spring Boot starter](modules/creditas-api-spring-boot-starter/README.md) | `creditas.*` in `application.yml`, a job that marks invoices paid |
| [Creditas API](.docs/creditas-api.md) | what the bank offers, issuing a key, the endpoints, what is still unknown |
| [Development](.docs/development.md) | build, tests, releasing to Maven Central |
| [Creditas: Creditas API](https://www.creditas.cz/creditas-api) | the bank's own page, with its PDF documentation |
| [PremiumAPI on SwaggerHub](https://app.swaggerhub.com/apis/Creditas/PremiumAPI/1.0.1) | the specification this client follows |

`make build` builds and checks it; `make` lists the rest. Licence: [Apache 2.0](LICENSE)
