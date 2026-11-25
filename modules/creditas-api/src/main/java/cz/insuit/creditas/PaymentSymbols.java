package cz.insuit.creditas;

import org.jspecify.annotations.Nullable;

/**
 * The Czech payment symbols of a transfer, each a string of digits with its leading zeros kept: a symbol is an
 * identifier, not a number. A missing one is null.
 */
public record PaymentSymbols(
        @Nullable String variable,
        @Nullable String constant,
        @Nullable String specific) {}
