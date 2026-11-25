package cz.insuit.creditas;

import static java.util.Objects.requireNonNull;

import org.jspecify.annotations.Nullable;

/** {@code AccountTransaction} of PremiumAPI 1.0.1, the fields this library reads. */
record TransactionJson(
        String transactionId,
        String category,
        String type,
        MoneyJson amount,
        String effectiveDate,
        @Nullable PartnerAccountJson partnerAccount,
        @Nullable String variableSymbol,
        @Nullable String constantSymbol,
        @Nullable String specificSymbol,
        @Nullable String remittanceInfo,
        @Nullable String payersReference,
        @Nullable MoneyJson balance) {

    TransactionJson {
        requireNonNull(transactionId, "transactionId");
        requireNonNull(category, "category");
        requireNonNull(type, "type");
        requireNonNull(amount, "amount");
        requireNonNull(effectiveDate, "effectiveDate");
    }
}
