package cz.insuit.creditas;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** The answer of {@code account/transaction/search}. An empty page may leave {@code transactions} out. */
record TransactionsPageJson(@Nullable List<TransactionJson> transactions) {

    List<TransactionJson> items() {
        return transactions == null ? List.of() : transactions;
    }
}
