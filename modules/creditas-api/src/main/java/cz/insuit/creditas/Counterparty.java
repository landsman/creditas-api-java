package cz.insuit.creditas;

import org.jspecify.annotations.Nullable;

/**
 * The other side of a transaction as Creditas names it. Any part may be missing: a card payment or a fee has no
 * account. Its {@code toString()} leaves out the name and the number, so logging a transaction leaks no person.
 *
 * @param number the account number; a domestic one as Creditas writes it ({@code 19-2000145399}), else an IBAN
 * @param bankCode the Czech bank code ({@code 0800}) or a BIC
 * @param bankName the bank's name
 * @param name a person's name and surname, or a company's name
 */
public record Counterparty(
        @Nullable String number,
        @Nullable String bankCode,
        @Nullable String bankName,
        @Nullable String name) {

    @Override
    public String toString() {
        return "Counterparty[bankCode=" + bankCode + "]";
    }
}
