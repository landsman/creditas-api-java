package cz.insuit.creditas;

import org.jspecify.annotations.Nullable;

/** {@code AccountTransaction.partnerAccount}. */
record PartnerAccountJson(
        @Nullable String number,
        @Nullable String bankCode,
        @Nullable String bankName,
        @Nullable String partnerName) {}
