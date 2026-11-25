package cz.insuit.creditas.spring;

import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code creditas.*}.
 *
 * @param key one account's key, for an application that reads only that one; it then gets an {@code ApiKey} bean.
 *     An application serving many account holders leaves it unset and passes each one's key with the call. Set it
 *     from the environment, never in a committed file.
 * @param accountId that account's id, from the key's detail in internet banking; read it from this record
 * @param baseUri where the API lives; the client's own default, production, unless set
 * @param requestTimeout how long one call may take; the client's own default unless set
 */
@ConfigurationProperties("creditas")
public record CreditasProperties(
        @Nullable String key,
        @Nullable String accountId,
        @Nullable URI baseUri,
        @Nullable Duration requestTimeout) {

    /** Leaves out the key, so logging the properties leaks nothing. */
    @Override
    public String toString() {
        return "CreditasProperties[accountId=" + accountId + ", baseUri=" + baseUri + ", requestTimeout="
                + requestTimeout + "]";
    }
}
