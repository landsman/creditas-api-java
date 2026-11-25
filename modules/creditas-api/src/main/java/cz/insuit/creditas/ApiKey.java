package cz.insuit.creditas;

import static java.util.Objects.requireNonNull;

/**
 * The key an account holder issues in internet banking (Správa, Otevřené bankovnictví, Klíče). One key opens one
 * account. Its {@code toString()} never prints it.
 */
public record ApiKey(String value) {

    /**
     * Keeps the ASCII letters and digits only, the key has nothing else: a key pasted from a PDF or a web page brings
     * no-break and zero-width spaces that would otherwise be sent with it.
     *
     * @throws IllegalArgumentException when nothing is left
     */
    public ApiKey {
        requireNonNull(value, "value");
        var kept = new StringBuilder(value.length());
        value.chars().filter(c -> c < 128 && Character.isLetterOrDigit(c)).forEach(c -> kept.append((char) c));
        if (kept.isEmpty()) {
            throw new IllegalArgumentException("the key is blank");
        }
        value = kept.toString();
    }

    @Override
    public String toString() {
        return "ApiKey[…]";
    }
}
