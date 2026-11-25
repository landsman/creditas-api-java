package cz.insuit.creditas.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import cz.insuit.creditas.ApiKey;
import cz.insuit.creditas.CreditasClient;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;

class CreditasAutoConfigurationTest {

    private final ApplicationContextRunner context =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CreditasAutoConfiguration.class));

    @Test
    void givesAClientAndNoKeyWhenNoneIsSet() {
        context.run(app -> {
            assertNotNull(app.getBean(CreditasClient.class));
            assertFalse(app.containsBean("creditasApiKey"));
        });
    }

    @Test
    void givesTheKeyOfTheOneAccountWhenItIsSet() {
        // What a pasted key carries inside it; TestPropertyValues trims only ASCII whitespace, so these reach ApiKey.
        context.withPropertyValues("creditas.key=Ab\u00A012\u200B", "creditas.account-id=acc-1")
                .run(app -> {
                    assertEquals(
                            "Ab\u00A012\u200B",
                            app.getBean(CreditasProperties.class).key());
                    assertEquals("Ab12", app.getBean(ApiKey.class).value());
                    assertEquals("acc-1", app.getBean(CreditasProperties.class).accountId());
                });
    }

    @Test
    void aKeyWithNothingInItStopsTheApplication() {
        context.withPropertyValues("creditas.key=\u00A0\u200B").run(app -> assertRefusedByTheLibrary(app));
    }

    @Test
    void aBaseUriThatIsNotOneStopsTheApplication() {
        context.withPropertyValues("creditas.base-uri=api.creditas.cz").run(app -> assertRefusedByTheLibrary(app));
    }

    @Test
    void aTimeoutThatCannotBeUsedStopsTheApplication() {
        context.withPropertyValues("creditas.request-timeout=0s").run(app -> assertRefusedByTheLibrary(app));
    }

    @Test
    void callsTheBaseUriItIsGiven() throws IOException {
        var path = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            var body = "{\"currency\":\"CZK\",\"currentBalance\":\"1\",\"availableBalance\":\"1\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            var host = server.getAddress().getAddress().getHostAddress();
            var base = "http://" + (host.contains(":") ? "[" + host + "]" : host) + ":"
                    + server.getAddress().getPort() + "/oam";
            context.withPropertyValues("creditas.base-uri=" + base, "creditas.request-timeout=5s")
                    .run(app -> app.getBean(CreditasClient.class).balance(new ApiKey("Ab12"), "acc-1"));
            assertEquals("/oam/v1/account/balance/get", path.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void backsOffForTheApplicationsOwnClient() {
        context.withUserConfiguration(OwnClient.class).run(app -> {
            assertSame(app.getBean(OwnClient.class).client, app.getBean(CreditasClient.class));
        });
    }

    /** The client's own check, not any startup failure: a missing class on another Spring Boot would also fail one. */
    private static void assertRefusedByTheLibrary(AssertableApplicationContext app) {
        var cause = NestedExceptionUtils.getMostSpecificCause(Objects.requireNonNull(app.getStartupFailure()));
        assertInstanceOf(IllegalArgumentException.class, cause);
        var frame = cause.getStackTrace()[0];
        assertTrue(frame.getClassName().startsWith("cz.insuit.creditas."), frame.toString());
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnClient {
        final CreditasClient client = new CreditasClient();

        @Bean
        CreditasClient ownCreditasClient() {
            return client;
        }
    }
}
