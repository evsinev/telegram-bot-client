package com.payneteasy.telegram.bot.client.http;

import static com.payneteasy.telegram.bot.client.http.TelegramLogScrubber.scrub;

/**
 * Stand-in for a throwable whose message may carry a secret.
 *
 * An exception outlives the log record it was reported in and travels out along the cause chain,
 * so the original object cannot be attached: {@code MalformedURLException} names the whole URL it
 * failed to parse, and in URL mode that URL holds the token. Keeping the type name, the scrubbed
 * message and the stack trace keeps the diagnosis and drops the secret.
 */
class ScrubbedCause extends RuntimeException {

    private ScrubbedCause(Throwable aCause) {
        super(aCause.getClass().getName() + ": " + scrub(aCause.getMessage()), sanitize(aCause.getCause()), false, false);
        setStackTrace(aCause.getStackTrace());
    }

    /**
     * @return a scrubbed copy of the whole chain, or null when there is nothing to copy
     */
    static ScrubbedCause sanitize(Throwable aCause) {
        return aCause == null ? null : new ScrubbedCause(aCause);
    }
}
