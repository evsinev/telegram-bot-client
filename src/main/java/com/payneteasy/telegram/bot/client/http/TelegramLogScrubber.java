package com.payneteasy.telegram.bot.client.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Removes anything secret-shaped from a string on its way into a log record or into an
 * exception message.
 *
 * The rule this class exists to enforce: a string that came from the network, or that
 * contains an assembled URL, reaches a log record or an exception message only through
 * {@link #scrub(String)} or {@link #scrubBody(String)}. Enumerating the places instead of
 * stating the rule has already proved to be an incomplete list twice.
 *
 * Mirrors {@code telegram-proxy/src/scrub.rs} so that both ends of the connection hide the
 * same things: {@link #scrubBody(String)} corresponds to its {@code scrub_body}, walking the
 * body as JSON, and {@link #scrub(String)} to its flat {@code scrub}.
 */
public final class TelegramLogScrubber {

    private static final String MASK = "***";

    /** Field whose value is replaced wholesale — 64 hex characters that no pattern would catch. */
    private static final String SECRET_TOKEN_FIELD_NAME = "secret_token";

    /**
     * The same field in flat text. Only the fallback path needs it: once a body parses as JSON,
     * the field is found by name and this pattern never runs.
     */
    private static final Pattern SECRET_TOKEN_FIELD = Pattern.compile("(\"secret_token\"\\s*:\\s*\")[^\"]*(\")");

    /**
     * Value of a sensitive query parameter, ending where the query does. Both the bare separators
     * and the form Gson escapes them into are accepted: unlike the proxy, whose flat rules only
     * ever see real URLs, this one also gets handed a body that failed to parse — a truncated one,
     * say — where {@code =} and {@code &} still read as {@code =} and {@code &}.
     */
    private static final Pattern SENSITIVE_PARAM = Pattern.compile(
            "((?:\\?|&|\\\\u0026)(?:bot_token|secret_token|token)(?:=|\\\\u003[dD]))[^&\\s\"'<>]*");

    /** {@code <bot_id>:<secret>}, taken from {@code scrub.rs:29-30}. */
    private static final Pattern TOKEN = Pattern.compile("([0-9]{1,20}):[A-Za-z0-9_-]{20,256}");

    private TelegramLogScrubber() {
    }

    /**
     * Scrub a response or request body. Parsing it undoes Gson's escaping in one move, which is
     * what makes the query-parameter rule work on a value that is a URL — chasing escape forms
     * with a regex closes one of them at a time.
     *
     * A body that does not parse (truncated, an HTML error page from an intermediary) falls back
     * to the flat rules.
     */
    public static String scrubBody(String aBody) {
        if (aBody == null || aBody.isEmpty()) {
            return aBody;
        }
        try {
            JsonElement parsed = new JsonParser().parse(aBody);
            if (parsed.isJsonObject() || parsed.isJsonArray()) {
                return scrubJson(parsed).toString();
            }
        } catch (RuntimeException e) {
            // not JSON — the flat rules still apply
        }
        return scrub(aBody);
    }

    /**
     * Scrub a flat string: a URL, an exception message, anything that is not a body.
     *
     * Three steps, in this order, as in {@code scrub.rs:44-65}: the {@code secret_token} field,
     * the sensitive query parameters, and the token pattern as a backstop for formats we did not
     * foresee. The {@code bot_id} before the colon is kept, so a log line still says which bot it
     * is about.
     */
    public static String scrub(String aText) {
        if (aText == null || aText.isEmpty()) {
            return aText;
        }
        String text = SECRET_TOKEN_FIELD.matcher(aText).replaceAll("$1" + MASK + "$2");
        text = SENSITIVE_PARAM.matcher(text).replaceAll("$1" + MASK);
        return maskTokens(text);
    }

    private static JsonElement scrubJson(JsonElement aElement) {
        if (aElement.isJsonObject()) {
            JsonObject scrubbed = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : aElement.getAsJsonObject().entrySet()) {
                String key = entry.getKey();
                scrubbed.add(scrub(key), SECRET_TOKEN_FIELD_NAME.equals(key)
                        ? new JsonPrimitive(MASK)
                        : scrubJson(entry.getValue()));
            }
            return scrubbed;
        }
        if (aElement.isJsonArray()) {
            JsonArray scrubbed = new JsonArray();
            for (JsonElement item : aElement.getAsJsonArray()) {
                scrubbed.add(scrubJson(item));
            }
            return scrubbed;
        }
        if (aElement.isJsonPrimitive() && aElement.getAsJsonPrimitive().isString()) {
            return new JsonPrimitive(scrub(aElement.getAsString()));
        }
        return aElement;
    }

    /**
     * Percent-encoding can hide the separator: {@code 123%3AAAH...} does not match the token
     * pattern while {@code 123:AAH...} does. As in {@code scrub.rs:45-65}, the decoded copy is
     * chosen first and every match is then masked — masking the original and stopping there would
     * leave an encoded token that sits next to a plain one untouched.
     */
    private static String maskTokens(String aText) {
        String candidate = aText;

        String decoded = percentDecode(aText);
        if (decoded != null && TOKEN.matcher(decoded).find()) {
            candidate = decoded;
        }

        return TOKEN.matcher(candidate).replaceAll("$1:" + MASK);
    }

    /**
     * Decode {@code %XX} and nothing else.
     *
     * Hand-written because {@link java.net.URLDecoder} has the wrong semantics here on both
     * counts: it throws on a stray {@code %} — "100% done" would cost us the whole string, and
     * with it any token further along — and it maps {@code +} to a space, which the percent
     * decoder the proxy uses does not. A malformed escape is left exactly as it stands.
     *
     * @return the decoded copy, or null when there was nothing to decode
     */
    private static String percentDecode(String aText) {
        if (aText.indexOf('%') < 0) {
            return null;
        }

        StringBuilder out     = new StringBuilder(aText.length());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        boolean               any   = false;

        for (int i = 0; i < aText.length(); ) {
            int value = i + 2 < aText.length() && aText.charAt(i) == '%' ? hexPair(aText, i + 1) : -1;
            if (value < 0) {
                flush(bytes, out);
                out.append(aText.charAt(i));
                i++;
            } else {
                bytes.write(value);
                any = true;
                i += 3;
            }
        }
        flush(bytes, out);

        return any ? out.toString() : null;
    }

    private static void flush(ByteArrayOutputStream aBytes, StringBuilder aOut) {
        if (aBytes.size() > 0) {
            aOut.append(new String(aBytes.toByteArray(), UTF_8));
            aBytes.reset();
        }
    }

    private static int hexPair(String aText, int aFrom) {
        int high = Character.digit(aText.charAt(aFrom), 16);
        int low  = Character.digit(aText.charAt(aFrom + 1), 16);
        return high < 0 || low < 0 ? -1 : high * 16 + low;
    }
}
