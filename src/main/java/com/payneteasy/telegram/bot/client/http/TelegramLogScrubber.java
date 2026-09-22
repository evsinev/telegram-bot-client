package com.payneteasy.telegram.bot.client.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.regex.Pattern;

/**
 * Removes anything secret-shaped from a string on its way into a log record or into an
 * exception message.
 *
 * The rule this class exists to enforce: a string that came from the network, or that
 * contains an assembled URL, reaches a log record or an exception message only through
 * {@link #scrub(String)}. Enumerating the places instead of stating the rule has already
 * proved to be an incomplete list twice.
 *
 * Three steps, in this order, mirroring {@code telegram-proxy/src/scrub.rs}:
 *
 * <ol>
 *   <li>the value of the JSON field {@code secret_token} — 64 hex characters, which neither
 *       of the other two rules would recognise;</li>
 *   <li>the values of the query parameters {@code bot_token}, {@code token} and
 *       {@code secret_token}, replaced whole, whatever they look like — this is the main
 *       defence for the webhook URL;</li>
 *   <li>the token pattern itself, as a backstop for formats we did not foresee. The
 *       {@code bot_id} before the colon is kept, so a log line still says which bot it is
 *       about.</li>
 * </ol>
 */
public final class TelegramLogScrubber {

    /** Value of the JSON field {@code secret_token}, in both compact and pretty-printed form. */
    private static final Pattern SECRET_TOKEN_FIELD = Pattern.compile("(\"secret_token\"\\s*:\\s*\")[^\"]*(\")");

    /**
     * Value of a sensitive query parameter. Ends where the query does: at the next parameter,
     * at whitespace, or at the quote closing the JSON string the URL sits in.
     *
     * The separator is matched in both forms, because Gson escapes {@code =} as {@code \\u003d}
     * by default: inside a serialized request body the parameter reads {@code bot_token\\u003d...},
     * and a rule that only knew the bare {@code =} would silently do nothing there.
     */
    private static final Pattern SENSITIVE_PARAM = Pattern.compile("([?&](?:bot_token|secret_token|token)(?:=|\\\\u003[dD]))[^&\\s\"'<>]*");

    /** {@code <bot_id>:<secret>}, taken from {@code scrub.rs}. */
    private static final Pattern TOKEN = Pattern.compile("([0-9]{1,20}):[A-Za-z0-9_-]{20,256}");

    private TelegramLogScrubber() {
    }

    public static String scrub(String aText) {
        if (aText == null || aText.isEmpty()) {
            return aText;
        }
        String text = SECRET_TOKEN_FIELD.matcher(aText).replaceAll("$1***$2");
        text = SENSITIVE_PARAM.matcher(text).replaceAll("$1***");
        return maskTokens(text);
    }

    /**
     * Percent-encoding can hide the separator: {@code 123%3AAAH...} does not match the token
     * pattern while {@code 123:AAH...} does. The decoded copy replaces the original only when
     * decoding actually reveals a token, so a string that was fine keeps its exact shape.
     */
    private static String maskTokens(String aText) {
        String masked = TOKEN.matcher(aText).replaceAll("$1:***");
        if (!masked.equals(aText)) {
            return masked;
        }

        String decoded = percentDecode(aText);
        if (decoded == null) {
            return aText;
        }
        String maskedDecoded = TOKEN.matcher(decoded).replaceAll("$1:***");
        return maskedDecoded.equals(decoded) ? aText : maskedDecoded;
    }

    private static String percentDecode(String aText) {
        if (aText.indexOf('%') < 0) {
            return null;
        }
        try {
            String decoded = URLDecoder.decode(aText, "UTF-8");
            return decoded.equals(aText) ? null : decoded;
        } catch (IllegalArgumentException | UnsupportedEncodingException e) {
            return null;
        }
    }
}
