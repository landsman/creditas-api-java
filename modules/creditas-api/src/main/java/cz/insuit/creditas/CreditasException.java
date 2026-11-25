package cz.insuit.creditas;

import org.jspecify.annotations.Nullable;

/**
 * A call to Creditas that did not return what was asked for, one subtype per thing a caller does about it, so a
 * {@code switch} over them is exhaustive. The message never carries the key, nor Creditas's response body.
 *
 * <p>Creditas answers every error with HTTP 500 and names it in the body, so the subtype comes from that name, not
 * from the status.
 */
public abstract sealed class CreditasException extends RuntimeException {

    CreditasException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    /** {@code OAM_SecurityExc}: the key is missing, wrong, expired or revoked. Retrying will not help. */
    public static final class InvalidKey extends CreditasException {
        public InvalidKey(String message) {
            super(message, null);
        }
    }

    /**
     * {@code SYS_ValidationExc} or {@code SYS_DataValidationExc}: Creditas refused the request itself, such as an
     * account id the key does not open. Retrying the same request will not help.
     */
    public static final class Rejected extends CreditasException {
        private final String name;
        private final @Nullable String code;

        public Rejected(String message, String name, @Nullable String code) {
            super(message, null);
            this.name = name;
            this.code = code;
        }

        /** The error's name as Creditas sent it. */
        public String name() {
            return name;
        }

        /** The first validation code, {@code VAERR2011}, when Creditas sent one. */
        public @Nullable String code() {
            return code;
        }
    }

    /**
     * Any other failure Creditas answered: {@code SYS_UnexpectedExc}, an error name this version does not know, or a
     * status with no Creditas error in its body, such as a gateway's 502. Retrying later may help.
     */
    public static final class Failed extends CreditasException {
        private final int status;
        private final @Nullable String body;

        public Failed(String message, int status, @Nullable String body) {
            super(message, null);
            this.status = status;
            this.body = body;
        }

        /** The HTTP status. */
        public int status() {
            return status;
        }

        /** The first 2,000 characters of the answer; kept out of the message, since it may hold account data. */
        public @Nullable String body() {
            return body;
        }
    }

    /**
     * No answer came: a timeout or a refused connection. An interrupted thread is not one: that is a {@link
     * java.util.concurrent.CancellationException}, so a retry does not repeat work someone cancelled.
     */
    public static final class NoAnswer extends CreditasException {
        public NoAnswer(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** An answer came, but not in a shape this library can read: not JSON, or a required key missing. */
    public static final class InvalidResponse extends CreditasException {
        public InvalidResponse(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }
}
