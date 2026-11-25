package cz.insuit.creditas.spring;

import static java.util.Objects.requireNonNull;

import cz.insuit.creditas.ApiKey;
import cz.insuit.creditas.CreditasClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * One {@link CreditasClient} for the application, built from {@code creditas.*}, unless the application defines its
 * own. Spring closes it on shutdown, which releases the HTTP client it made.
 */
@AutoConfiguration
@EnableConfigurationProperties(CreditasProperties.class)
public class CreditasAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CreditasClient creditasClient(CreditasProperties properties) {
        // Only what the application set is passed on, so each default lives in one place: the client.
        var builder = CreditasClient.builder();
        var baseUri = properties.baseUri();
        if (baseUri != null) {
            builder.baseUri(baseUri);
        }
        var requestTimeout = properties.requestTimeout();
        if (requestTimeout != null) {
            builder.requestTimeout(requestTimeout);
        }
        return builder.build();
    }

    /** Only with {@code creditas.key} set; one with nothing left once spaces are dropped stops the application. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty("creditas.key")
    public ApiKey creditasApiKey(CreditasProperties properties) {
        return new ApiKey(requireNonNull(properties.key(), "creditas.key"));
    }
}
