package cz.insuit.creditas;

import static java.util.Objects.requireNonNull;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * A movement on the account.
 *
 * @param id Creditas's id ({@code RLZ-820659828}), the one to deduplicate by
 * @param direction which way the money moved
 * @param category what kind of movement; {@link Category#OTHER} also stands for a value this version does not know
 * @param rawCategory the category exactly as Creditas sent it
 * @param amount positive for money in, negative for money out
 * @param effectiveDate the value date; Creditas sends no time of day
 * @param symbols the variable, constant and specific symbol
 * @param counterparty the other side, when there is one
 * @param message the payer's message for the recipient ({@code remittanceInfo})
 * @param payersReference the payer's reference for the recipient, up to 35 characters
 * @param balanceAfter the account's balance right after this movement, when Creditas sends it
 */
public record Transaction(
        String id,
        Direction direction,
        Category category,
        String rawCategory,
        Money amount,
        LocalDate effectiveDate,
        PaymentSymbols symbols,
        @Nullable Counterparty counterparty,
        @Nullable String message,
        @Nullable String payersReference,
        @Nullable Money balanceAfter) {

    public Transaction {
        requireNonNull(id, "id");
        requireNonNull(direction, "direction");
        requireNonNull(category, "category");
        requireNonNull(rawCategory, "rawCategory");
        requireNonNull(amount, "amount");
        requireNonNull(effectiveDate, "effectiveDate");
        requireNonNull(symbols, "symbols");
    }

    /** Which way the money moved. */
    public enum Direction {
        /** Money in. */
        CREDIT,
        /** Money out. */
        DEBIT
    }

    /**
     * What kind of movement it is. Creditas may add a value; it reads as {@link #OTHER}, with the real one in {@link
     * Transaction#rawCategory()}, so keep a {@code default} branch when switching over it.
     */
    public enum Category {
        /** A domestic transfer. */
        DOMESTIC,
        /** A foreign transfer. */
        FOREIGN,
        /** A card payment. */
        CARD,
        /** A cash deposit or withdrawal. */
        CASH,
        /** A fee the bank charged. */
        FEE,
        /** Anything else, or a value this version does not know. */
        OTHER
    }

    /** Leaves out the counterparty and the message, which may name a person. */
    @Override
    public String toString() {
        return "Transaction[id=" + id + ", " + direction + ", " + category + ", " + amount + ", " + effectiveDate + "]";
    }
}
