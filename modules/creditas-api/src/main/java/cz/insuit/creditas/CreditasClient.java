package cz.insuit.creditas;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads an account's balance and transactions through Creditas API. The key is passed with each call and never kept,
 * so one instance serves any number of account holders.
 *
 * <p>Thread-safe; one per application is enough. {@link #close()} releases the HTTP client it made itself, not one
 * passed to {@link Builder#httpClient}.
 */
public final class CreditasClient implements AutoCloseable {

    /** Creditas's production API. */
    public static final URI PRODUCTION = URI.create("https://api.creditas.cz/oam/");

    /** What working callers use; the API's own maximum is not documented. */
    static final int PAGE_SIZE = 50;
    // ponytail: a backstop against a server that never sends a short page; half a million movements is far past it.
    private static final int MAX_PAGES = 10_000;

    private static final int MAX_BODY = 2_000;
    private static final JsonMapper JSON = JsonMapper.builder()
            // Creditas sends more than the records name, and may add keys at any time.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final HttpClient http;
    private final boolean ownsHttp;
    private final URI baseUri;
    private final Duration requestTimeout;

    /** A client for production with defaults. */
    public CreditasClient() {
        this(builder());
    }

    private CreditasClient(Builder builder) {
        this.ownsHttp = builder.http == null;
        this.http = builder.http != null
                ? builder.http
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.baseUri = builder.baseUri;
        this.requestTimeout = builder.requestTimeout;
    }

    /** Starts a client for production; set only what differs. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void close() {
        if (ownsHttp) {
            http.close();
        }
    }

    /**
     * The account's balance.
     *
     * @param accountId the account's id from the key's detail in internet banking, not its number
     * @throws IllegalArgumentException when the account id is blank
     * @throws CreditasException when Creditas refuses, fails or answers in an unexpected shape; see its subtypes
     * @throws CancellationException when the thread is interrupted; its interrupt flag stays set
     */
    public Balance balance(ApiKey key, String accountId) {
        var body = new LinkedHashMap<String, Object>();
        body.put("accountId", accountIdOf(accountId));
        var json = parse("account/balance/get", post(key, "v1/account/balance/get", body), BalanceJson.class);
        var currency = currency(json.currency());
        return new Balance(
                json.iban(), new Money(json.currentBalance(), currency), new Money(json.availableBalance(), currency));
    }

    /**
     * The account's transactions whose value date falls between the two dates, both included, every page read.
     *
     * @param accountId the account's id from the key's detail in internet banking, not its number
     * @throws IllegalArgumentException when the account id is blank or {@code from} is after {@code to}
     * @throws CreditasException when Creditas refuses, fails or answers in an unexpected shape; see its subtypes
     * @throws CancellationException when the thread is interrupted; its interrupt flag stays set
     */
    public List<Transaction> transactions(ApiKey key, String accountId, LocalDate from, LocalDate to) {
        var id = accountIdOf(accountId);
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from " + from + " is after to " + to);
        }
        var filter = new LinkedHashMap<String, Object>();
        filter.put("dateFrom", from.toString());
        filter.put("dateTo", to.toString());
        var all = new ArrayList<Transaction>();
        for (int page = 0; page < MAX_PAGES; page++) {
            var body = new LinkedHashMap<String, Object>();
            body.put("accountId", id);
            body.put("filter", filter);
            body.put("pageItemCount", PAGE_SIZE);
            body.put("pageIndex", page);
            var items = parse(
                            "account/transaction/search",
                            post(key, "v1/account/transaction/search", body),
                            TransactionsPageJson.class)
                    .items();
            for (var item : items) {
                all.add(transaction(item));
            }
            // ponytail: whether itemCount is the page's count or the search's total is not documented, so a short
            // page is the end. Check it against a live account with more than 50 movements.
            if (items.size() < PAGE_SIZE) {
                return List.copyOf(all);
            }
        }
        throw new CreditasException.InvalidResponse(
                "account/transaction/search sent " + MAX_PAGES + " full pages and no end", null);
    }

    static Transaction transaction(TransactionJson json) {
        var direction = switch (json.type()) {
            case "CREDIT" -> Transaction.Direction.CREDIT;
            case "DEBIT" -> Transaction.Direction.DEBIT;
            // A wrong sign on money is worse than a failed read.
            default ->
                throw new CreditasException.InvalidResponse(
                        "transaction " + json.transactionId() + " has an unknown type '" + json.type() + "'", null);
        };
        var amount = money(json.amount());
        // ponytail: the sign Creditas puts on a debit is not documented, so either is read as money out. A credit
        // with a minus contradicts itself and is refused.
        if (direction == Transaction.Direction.CREDIT && amount.amount().signum() < 0) {
            throw new CreditasException.InvalidResponse(
                    "transaction " + json.transactionId() + " is a CREDIT with a negative amount", null);
        }
        var signed =
                direction == Transaction.Direction.DEBIT && amount.amount().signum() > 0 ? amount.negate() : amount;
        var partner = json.partnerAccount();
        return new Transaction(
                json.transactionId(),
                direction,
                category(json.category()),
                json.category(),
                signed,
                date(json.transactionId(), json.effectiveDate()),
                new PaymentSymbols(
                        blankToNull(json.variableSymbol()),
                        blankToNull(json.constantSymbol()),
                        blankToNull(json.specificSymbol())),
                partner == null
                        ? null
                        : new Counterparty(
                                blankToNull(partner.number()),
                                blankToNull(partner.bankCode()),
                                blankToNull(partner.bankName()),
                                blankToNull(partner.partnerName())),
                blankToNull(json.remittanceInfo()),
                blankToNull(json.payersReference()),
                json.balance() == null ? null : money(json.balance()));
    }

    private static Transaction.Category category(String value) {
        return switch (value) {
            case "DOMESTIC" -> Transaction.Category.DOMESTIC;
            case "FOREIGN" -> Transaction.Category.FOREIGN;
            case "CARD" -> Transaction.Category.CARD;
            case "CASH" -> Transaction.Category.CASH;
            case "FEE" -> Transaction.Category.FEE;
            default -> Transaction.Category.OTHER;
        };
    }

    private static LocalDate date(String transactionId, String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new CreditasException.InvalidResponse(
                    "transaction " + transactionId + " has effectiveDate '" + value + "', not a date", e);
        }
    }

    private static Money money(MoneyJson json) {
        return new Money(json.value(), currency(json.currency()));
    }

    private static Currency currency(String code) {
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new CreditasException.InvalidResponse("unknown currency '" + code + "'", e);
        }
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * The id as Creditas defines it: letters, digits, dashes and spaces. Anything else at either end goes, so an id
     * copied with a no-break or zero-width space still matches.
     */
    static String accountIdOf(String accountId) {
        requireNonNull(accountId, "accountId");
        int start = 0;
        int end = accountId.length();
        while (start < end && !idChar(accountId.charAt(start))) {
            start++;
        }
        while (end > start && !idChar(accountId.charAt(end - 1))) {
            end--;
        }
        if (start == end) {
            throw new IllegalArgumentException("accountId is blank");
        }
        return accountId.substring(start, end);
    }

    private static boolean idChar(char c) {
        return c < 128 && (Character.isLetterOrDigit(c) || c == '-');
    }

    /** One POST; the body of a 200, or the error Creditas named. */
    private String post(ApiKey key, String path, Map<String, Object> body) {
        var request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + key.value())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new CreditasException.NoAnswer(path + " got no answer: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            var cancelled = new CancellationException(path + " was interrupted");
            cancelled.initCause(e);
            throw cancelled;
        }
        if (response.statusCode() == 200) {
            return response.body();
        }
        throw error(path, response.statusCode(), response.body());
    }

    /** Creditas answers every error with 500 and names it in the body; the name decides, not the status. */
    static CreditasException error(String path, int status, @Nullable String body) {
        JsonNode node = null;
        if (body != null && !body.isBlank()) {
            try {
                node = JSON.readTree(body);
            } catch (JacksonException e) {
                // Not Creditas's error but a gateway's page; Failed below keeps its start.
            }
        }
        var name = node == null ? "" : node.path("name").asString("");
        var message = path + " failed: " + (name.isEmpty() ? "HTTP " + status : name);
        return switch (name) {
            case "OAM_SecurityExc" -> new CreditasException.InvalidKey(message);
            case "SYS_ValidationExc", "SYS_DataValidationExc" ->
                new CreditasException.Rejected(message, name, validationCode(requireNonNull(node)));
            default -> new CreditasException.Failed(message, status, excerpt(body));
        };
    }

    /** {@code data.validationResult} is an array in the specification and an object in the bank's PDF; both read. */
    private static @Nullable String validationCode(JsonNode error) {
        var result = error.path("data").path("validationResult");
        var first = result.isArray() ? result.path(0) : result;
        var code = first.path("validationErrorCode").asString("");
        return code.isEmpty() ? null : code;
    }

    private static @Nullable String excerpt(@Nullable String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        return body.length() > MAX_BODY ? body.substring(0, MAX_BODY) : body;
    }

    /** Anything thrown while reading a body, or a JSON {@code null}, is an answer without the expected shape. */
    private static <T> T parse(String what, String body, Class<T> type) {
        T value;
        try {
            value = JSON.readValue(body, type);
        } catch (JacksonException e) {
            throw new CreditasException.InvalidResponse(what + " returned a body this library cannot read", e);
        }
        if (value == null) {
            throw new CreditasException.InvalidResponse(what + " returned null", null);
        }
        return value;
    }

    /** Everything optional; {@code new CreditasClient()} is production with defaults. */
    public static final class Builder {

        private URI baseUri = PRODUCTION;
        private Duration requestTimeout = Duration.ofSeconds(30);
        private @Nullable HttpClient http;

        private Builder() {}

        /**
         * Where the API lives, {@link #PRODUCTION} unless set; a test server in tests. A path in it is kept.
         *
         * @throws IllegalArgumentException unless it is an absolute http(s) URI with a host, so a wrong setting fails
         *     at startup rather than on the first call
         */
        public Builder baseUri(URI baseUri) {
            var scheme = baseUri.getScheme();
            if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) || baseUri.getHost() == null) {
                throw new IllegalArgumentException("baseUri must be an absolute http(s) URI with a host: " + baseUri);
            }
            var text = baseUri.toString();
            this.baseUri = text.endsWith("/") ? baseUri : URI.create(text + "/");
            return this;
        }

        /** For a proxy, custom TLS or a test server; the caller closes it. */
        public Builder httpClient(HttpClient http) {
            this.http = http;
            return this;
        }

        /**
         * How long one request may take; 30 seconds unless set.
         *
         * @throws IllegalArgumentException when it is zero or negative, which every request would refuse; here it
         *     fails at startup rather than on the first call
         */
        public Builder requestTimeout(Duration requestTimeout) {
            if (requestTimeout.isNegative() || requestTimeout.isZero()) {
                throw new IllegalArgumentException("requestTimeout must be positive: " + requestTimeout);
            }
            this.requestTimeout = requestTimeout;
            return this;
        }

        /** The client; the builder can be reused for another. */
        public CreditasClient build() {
            return new CreditasClient(this);
        }
    }
}
