package cz.insuit.creditas;

import static java.util.Objects.requireNonNull;

import org.jspecify.annotations.Nullable;

/**
 * An account's balance.
 *
 * @param iban the account's IBAN, when Creditas sends it
 * @param current the booked balance, which may not hold the movements of the current business day yet
 * @param available what can be spent: may include pending movements and an overdraft
 */
public record Balance(@Nullable String iban, Money current, Money available) {

    public Balance {
        requireNonNull(current, "current");
        requireNonNull(available, "available");
    }
}
