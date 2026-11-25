package cz.insuit.creditas;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** The answer of {@code account/balance/get}: both amounts in the one currency. */
record BalanceJson(@Nullable String iban, String currency, BigDecimal currentBalance, BigDecimal availableBalance) {

    BalanceJson {
        requireNonNull(currency, "currency");
        requireNonNull(currentBalance, "currentBalance");
        requireNonNull(availableBalance, "availableBalance");
    }
}
