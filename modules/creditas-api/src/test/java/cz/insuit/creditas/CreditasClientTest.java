package cz.insuit.creditas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CreditasClientTest {

    private static final ApiKey KEY = new ApiKey("a".repeat(64));
    private static final Currency CZK = Currency.getInstance("CZK");
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);

    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private @Nullable HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void readsTransactionsAsCreditasSendsThem() {
        try (var client = client(request -> new Answer(200, fixture("transactions-page-0.json")))) {
            var transactions = client.transactions(KEY, "ded30c093bbe9c2d6e8735c8b4a7efc8a4bfbcc4", FROM, TO);

            assertEquals(2, transactions.size());
            var credit = transactions.get(0);
            assertEquals("RLZ-820659828", credit.id());
            assertEquals(Transaction.Direction.CREDIT, credit.direction());
            assertEquals(Transaction.Category.DOMESTIC, credit.category());
            assertEquals(new Money(new BigDecimal("1000"), CZK), credit.amount());
            assertEquals(LocalDate.of(2026, 11, 23), credit.effectiveDate());
            assertEquals(new PaymentSymbols("0123456", "0308", null), credit.symbols());
            assertEquals(
                    new Counterparty("1900000007", "0800", "Česká spořitelna, a.s.", "Jana Nováková"),
                    credit.counterparty());
            assertEquals("Faktura 2026017", credit.message());
            assertEquals("INV-2026-17", credit.payersReference());
            assertEquals(new Money(new BigDecimal("97163.53"), CZK), credit.balanceAfter());

            var card = transactions.get(1);
            assertEquals(new Money(new BigDecimal("-249.90"), CZK), card.amount());
            assertEquals(Transaction.Category.CARD, card.category());
            assertNull(card.counterparty());
            assertNull(card.balanceAfter());
        }
        var request = requests.getFirst();
        assertEquals("/oam/v1/account/transaction/search", request.path());
        assertEquals("Bearer " + "a".repeat(64), request.authorization());
        assertTrue(request.body().contains("\"dateFrom\":\"2026-11-01\""), request.body());
        assertTrue(request.body().contains("\"dateTo\":\"2026-11-30\""), request.body());
        assertTrue(request.body().contains("\"pageIndex\":0"), request.body());
    }

    @Test
    void readsPagesUntilAShortOne() {
        try (var client = client(request -> {
            int page = request.body().contains("\"pageIndex\":0") ? 0 : 1;
            return new Answer(200, page(page == 0 ? CreditasClient.PAGE_SIZE : 3, page));
        })) {
            var transactions = client.transactions(KEY, "acc", FROM, TO);

            assertEquals(CreditasClient.PAGE_SIZE + 3, transactions.size());
            assertEquals(
                    CreditasClient.PAGE_SIZE + 3,
                    transactions.stream().map(Transaction::id).distinct().count());
        }
        assertEquals(2, requests.size());
        assertTrue(requests.get(1).body().contains("\"pageIndex\":1"));
    }

    @Test
    void anEmptyPageWithoutTheKeyIsNoTransactions() {
        try (var client = client(request -> new Answer(200, "{\"itemCount\":0}"))) {
            assertEquals(List.of(), client.transactions(KEY, "acc", FROM, TO));
        }
    }

    @Test
    void readsTheBalance() {
        try (var client = client(request -> new Answer(200, fixture("balance.json")))) {
            var balance = client.balance(KEY, "acc");

            assertEquals("CZ1422500000000106023999", balance.iban());
            assertEquals(new Money(new BigDecimal("2522.53"), CZK), balance.current());
            assertEquals(new Money(new BigDecimal("2022.50"), CZK), balance.available());
        }
        assertEquals("/oam/v1/account/balance/get", requests.getFirst().path());
        assertEquals("{\"accountId\":\"acc\"}", requests.getFirst().body());
    }

    @Test
    void aRefusedKeyIsInvalidKeyThoughCreditasSays500() {
        try (var client =
                client(request -> new Answer(500, "{\"name\":\"OAM_SecurityExc\",\"message\":\"Access denied\"}"))) {
            assertThrows(CreditasException.InvalidKey.class, () -> client.balance(KEY, "acc"));
        }
    }

    @Test
    void aValidationErrorKeepsItsCodeAsAnArrayOrAnObject() {
        var asArray = CreditasClient.error(
                "p",
                500,
                "{\"name\":\"SYS_ValidationExc\",\"data\":{\"validationResult\":[{\"validationErrorCode\":\"VAERR2011\"}]}}");
        var asObject = CreditasClient.error(
                "p",
                500,
                "{\"name\":\"SYS_ValidationExc\",\"data\":{\"validationResult\":{\"validationErrorCode\":\"VAERR2006\"}}}");

        assertEquals(
                "VAERR2011",
                assertInstanceOf(CreditasException.Rejected.class, asArray).code());
        assertEquals(
                "VAERR2006",
                assertInstanceOf(CreditasException.Rejected.class, asObject).code());
    }

    @Test
    void anythingElseIsFailedWithTheBodyOutOfTheMessage() {
        var unexpected = CreditasClient.error("p", 500, "{\"name\":\"SYS_UnexpectedExc\",\"message\":\"x\"}");
        var gateway = CreditasClient.error("p", 502, "<html>Bad gateway</html>");

        assertEquals(
                500,
                assertInstanceOf(CreditasException.Failed.class, unexpected).status());
        var failed = assertInstanceOf(CreditasException.Failed.class, gateway);
        assertEquals("<html>Bad gateway</html>", failed.body());
        assertFalse(Objects.requireNonNull(failed.getMessage()).contains("html"));
    }

    @Test
    void aCreditWithAMinusIsRefusedNotGuessed() {
        var json = transactionJson("1", "CREDIT", "-5.00", "DOMESTIC");

        assertThrows(CreditasException.InvalidResponse.class, () -> CreditasClient.transaction(json));
    }

    @Test
    void aDebitIsMoneyOutWhicheverSignCreditasPutsOnIt() {
        var unsigned = CreditasClient.transaction(transactionJson("1", "DEBIT", "5.00", "DOMESTIC"));
        var signed = CreditasClient.transaction(transactionJson("2", "DEBIT", "-5.00", "DOMESTIC"));

        assertEquals(new Money(new BigDecimal("-5"), CZK), unsigned.amount());
        assertEquals(new Money(new BigDecimal("-5"), CZK), signed.amount());
    }

    @Test
    void anUnknownTypeIsRefused() {
        assertThrows(
                CreditasException.InvalidResponse.class,
                () -> CreditasClient.transaction(transactionJson("1", "REVERSAL", "5.00", "DOMESTIC")));
    }

    @Test
    void anUnknownCategoryIsOtherWithTheRawValueKept() {
        var transaction = CreditasClient.transaction(transactionJson("1", "CREDIT", "5.00", "CRYPTO"));

        assertEquals(Transaction.Category.OTHER, transaction.category());
        assertEquals("CRYPTO", transaction.rawCategory());
    }

    @Test
    void aMissingRequiredKeyIsInvalidResponse() {
        try (var client = client(request -> new Answer(200, "{\"transactions\":[{\"transactionId\":\"1\"}]}"))) {
            assertThrows(CreditasException.InvalidResponse.class, () -> client.transactions(KEY, "acc", FROM, TO));
        }
    }

    @Test
    void reversedDatesAreACallersMistake() {
        try (var client = new CreditasClient()) {
            assertThrows(IllegalArgumentException.class, () -> client.transactions(KEY, "acc", TO, FROM));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u00A0acc-1\u00A0", "\u202Facc-1", "acc-1\u200B", "\uFEFFacc-1", "\tacc-1 "})
    void anAccountIdLosesWhatAPasteBringsAtItsEnds(String pasted) {
        assertEquals("acc-1", CreditasClient.accountIdOf(pasted));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\u00A0", "\u200B\uFEFF"})
    void aBlankAccountIdIsRefused(String blank) {
        assertThrows(IllegalArgumentException.class, () -> CreditasClient.accountIdOf(blank));
    }

    private CreditasClient client(Function<Request, Answer> answer) {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var server = Objects.requireNonNull(this.server);
        server.createContext("/", exchange -> {
            var request = new Request(
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requests.add(request);
            var reply = answer.apply(request);
            var bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return CreditasClient.builder()
                .baseUri(URI.create(
                        "http://" + hostOf(server) + ":" + server.getAddress().getPort() + "/oam"))
                .build();
    }

    // An IPv6 loopback goes in brackets in a URI.
    private static String hostOf(HttpServer server) {
        var host = server.getAddress().getAddress().getHostAddress();
        return host.contains(":") ? "[" + host + "]" : host;
    }

    private static String page(int size, int page) {
        var items = new ArrayList<String>();
        IntStream.range(0, size)
                .forEach(i -> items.add("{\"transactionId\":\"RLZ-" + page + "-" + i
                        + "\",\"category\":\"DOMESTIC\",\"type\":\"CREDIT\",\"code\":\"76\","
                        + "\"amount\":{\"value\":\"1.00\",\"currency\":\"CZK\"},\"effectiveDate\":\"2026-11-02\"}"));
        return items.stream().collect(Collectors.joining(",", "{\"transactions\":[", "],\"itemCount\":" + size + "}"));
    }

    private static TransactionJson transactionJson(String id, String type, String amount, String category) {
        return new TransactionJson(
                id,
                category,
                type,
                new MoneyJson(new BigDecimal(amount), "CZK"),
                "2026-11-02",
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static String fixture(String name) {
        try (var in = CreditasClientTest.class.getResourceAsStream("/creditas/" + name)) {
            return new String(Objects.requireNonNull(in, name).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Request(String path, @Nullable String authorization, String body) {}

    private record Answer(int status, String body) {}
}
