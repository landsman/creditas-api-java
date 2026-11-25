# Creditas API

What Banka CREDITAS offers, read 2026-11-25 from [the product page](https://www.creditas.cz/creditas-api),
[the bank's PDF](https://www.creditas.cz/files/otevrene-bankovnictvi-a-creditas-api-dokumentace.pdf) and the
specifications on SwaggerHub.

## Two ways in

| | Key from internet banking (this library) | OAuth 2 |
|---|---|---|
| Who | the account holder, or a person they authorised | a partner app the bank approved, or a licensed TPP |
| Scope | one account per key, any number of keys | every account the user consents to |
| Spec | [PremiumAPI 1.0.1](https://app.swaggerhub.com/apis/Creditas/PremiumAPI/1.0.1) | [OpenAPI 1.0.2](https://app.swaggerhub.com/apis/Creditas/OpenAPI/1.0.2) |

The specifications are not semver: 1.0.1 of the OpenAPI broke 1.0.0.

## Setting it up

In [internet banking](https://banking.creditas.cz): **Správa → Otevřené bankovnictví → Klíče → Vytvořit nový klíč**.
Pick the account, the permission (*Oprávnění ke čtení* is enough to read payments) and a name, accept the terms,
confirm with mPIN or SMS. The key's detail shows the key and the account's `accountId`; the application needs both.
A key has a validity date and is revoked by the × beside it in the same list.

## The calls this library makes

Base `https://api.creditas.cz/oam/`, every call a `POST` with a JSON body, the key as `Authorization: Bearer`.

| Call | Body |
|---|---|
| `v1/account/balance/get` | `{accountId}` |
| `v1/account/transaction/search` | `{accountId, filter: {dateFrom, dateTo}, pageItemCount: 50, pageIndex}`, pages from 0 |

The date filter is on the value date. Every error is HTTP 500 with `{name, message, data?}`: `OAM_SecurityExc` for a
bad key, `SYS_ValidationExc` and `SYS_DataValidationExc` for a refused request, `SYS_UnexpectedExc` for a fault.

## Open questions

What a live account has to answer, each one marked `ponytail:` in the code where it is guessed:

- **The sign of a debit.** The spec says nothing; the client reads a debit as money out whichever sign it carries,
  and refuses a credit with a minus.
- **What `itemCount` counts**, this page or the whole search. The client stops at a page shorter than 50.
- The largest `pageItemCount` the API accepts, and any rate limit.
- Whether pending card payments show up before they are booked.
- Whether accounts moved to Creditas One keep the key screen.
- How long a key is valid by default.
