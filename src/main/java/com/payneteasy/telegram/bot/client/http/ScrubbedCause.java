package com.payneteasy.telegram.bot.client.http;

import java.util.IdentityHashMap;
import java.util.Map;

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

    private ScrubbedCause(Throwable aCause, Map<Throwable, Boolean> aSeen) {
        // writableStackTrace must stay true, or the setStackTrace below is a no-op and the copy
        // arrives with no frames at all
        super(aCause.getClass().getName() + ": " + scrub(aCause.getMessage()), sanitize(aCause.getCause(), aSeen), false, true);
        setStackTrace(aCause.getStackTrace());
    }

    /**
     * @return a scrubbed copy of the whole chain, or null when there is nothing to copy
     */
    static ScrubbedCause sanitize(Throwable aCause) {
        return sanitize(aCause, new IdentityHashMap<Throwable, Boolean>());
    }

    /**
     * The same, with every message dropped rather than scrubbed.
     *
     * For a failure to turn an answer into a response class: the parser quotes the fragment it
     * choked on, and that fragment is a piece of the body. The flat rules cannot help — a
     * `secret_token` is known by the name of its key, and a fragment has no structure — so the
     * text goes and the type names and stacks stay. The body itself is already in the log, walked
     * or withheld.
     */
    static ScrubbedCause typeOnly(Throwable aCause) {
        if (aCause == null) {
            return null;
        }
        ScrubbedCause copy = new ScrubbedCause(aCause.getClass().getName(), typeOnly(aCause.getCause()));
        copy.setStackTrace(aCause.getStackTrace());
        return copy;
    }

    private ScrubbedCause(String aMessage, ScrubbedCause aCause) {
        super(aMessage, aCause, false, true);
    }

    /**
     * A cause chain is allowed to be a cycle — {@code a.initCause(b); b.initCause(a)} is legal —
     * and walking one without remembering where we have been turns a failed call into a
     * StackOverflowError, which is an Error and so escapes every handler meant to catch this.
     */
    private static ScrubbedCause sanitize(Throwable aCause, Map<Throwable, Boolean> aSeen) {
        if (aCause == null || aSeen.put(aCause, Boolean.TRUE) != null) {
            return null;
        }
        return new ScrubbedCause(aCause, aSeen);
    }
}
