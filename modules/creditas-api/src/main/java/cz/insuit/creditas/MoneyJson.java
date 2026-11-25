package cz.insuit.creditas;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;

/** {@code Money}: a decimal string and an ISO 4217 code. */
record MoneyJson(BigDecimal value, String currency) {

    MoneyJson {
        requireNonNull(value, "value");
        requireNonNull(currency, "currency");
    }
}
