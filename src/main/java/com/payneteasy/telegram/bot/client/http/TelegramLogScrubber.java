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

    /**
     * Deeper than this and the subtree is masked wholesale. No Telegram body is anywhere near it,
     * and a walk that recurses as far as the input asks is a way to lose the walk itself.
     */
    private static final int MAX_DEPTH = 100;

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
        if (looksLikeJson(aBody)) {
            try {
                return scrubJson(new JsonParser().parse(aBody), 0).toString();
            } catch (RuntimeException e) {
                // truncated, or deeper than the parser can take - the flat rules still apply,
                // and they decode escapes themselves, so nothing rides on this succeeding
            }
        }
        return scrub(aBody);
    }

    /**
     * A body is taken through the parser only when it opens as an object or an array. Anything
     * else keeps its exact shape — run through the parser, a one-word error page would come back
     * quoted — and is handled by the flat rules, which decode escapes themselves. A bare JSON
     * string is a whole document too, but widening this to accept it would add a branch no test
     * can tell apart from the flat path.
     */
    private static boolean looksLikeJson(String aBody) {
        for (int i = 0; i < aBody.length(); i++) {
            char c = aBody.charAt(i);
            if (!Character.isWhitespace(c)) {
                return c == '{' || c == '[';
            }
        }
        return false;
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
        return applyRules(reveal(aText));
    }

    private static String applyRules(String aText) {
        String text = SECRET_TOKEN_FIELD.matcher(aText).replaceAll("$1" + MASK + "$2");
        text = SENSITIVE_PARAM.matcher(text).replaceAll("$1" + MASK);
        return TOKEN.matcher(text).replaceAll("$1:" + MASK);
    }

    /**
     * Pick the form of the string the rules see the most in.
     *
     * A secret can be hidden from a rule by encoding rather than by shape: {@code bot%5Ftoken=}
     * hides the parameter name, {@code 123456789\u003aAAH...} hides the separator of the token.
     * The proxy does not need this — its flat rules only ever see real URLs — but ours also gets
     * handed a body that did not go through the parser, where Gson's escapes are still in place.
     *
     * Counting matches rather than asking "did anything match" is what makes it safe: a string
     * holding one plain token and one encoded token matches either way, and stopping at the first
     * form that matched would leave the second untouched.
     */
    private static String reveal(String aText) {
        String best  = aText;
        int    found = countMatches(aText);

        String unescaped = jsonUnescape(aText);
        String[] candidates = {
                unescaped,
                percentDecode(aText),
                unescaped == null ? null : percentDecode(unescaped)
        };
        for (String candidate : candidates) {
            if (candidate != null && countMatches(candidate) > found) {
                best  = candidate;
                found = countMatches(candidate);
            }
        }
        return best;
    }

    private static int countMatches(String aText) {
        return count(SECRET_TOKEN_FIELD, aText) + count(SENSITIVE_PARAM, aText) + count(TOKEN, aText);
    }

    private static int count(Pattern aPattern, String aText) {
        java.util.regex.Matcher matcher = aPattern.matcher(aText);
        int found = 0;
        while (matcher.find()) {
            found++;
        }
        return found;
    }

    private static JsonElement scrubJson(JsonElement aElement, int aDepth) {
        if (aDepth > MAX_DEPTH) {
            return new JsonPrimitive(MASK);
        }
        if (aElement.isJsonObject()) {
            JsonObject scrubbed = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : aElement.getAsJsonObject().entrySet()) {
                String key = entry.getKey();
                scrubbed.add(scrub(key), SECRET_TOKEN_FIELD_NAME.equals(key)
                        ? new JsonPrimitive(MASK)
                        : scrubJson(entry.getValue(), aDepth + 1));
            }
            return scrubbed;
        }
        if (aElement.isJsonArray()) {
            JsonArray scrubbed = new JsonArray();
            for (JsonElement item : aElement.getAsJsonArray()) {
                scrubbed.add(scrubJson(item, aDepth + 1));
            }
            return scrubbed;
        }
        if (aElement.isJsonPrimitive() && aElement.getAsJsonPrimitive().isString()) {
            return new JsonPrimitive(scrub(aElement.getAsString()));
        }
        return aElement;
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

    /**
     * Decode {@code \\uXXXX} and nothing else.
     *
     * Gson escapes {@code =}, {@code &} and any non-ASCII character this way, so a body that did
     * not survive the parser can still be holding a secret that only looks harmless. Written by
     * hand for the same reason as the percent decoder: a malformed escape has to be left alone
     * rather than cost us the rest of the string.
     *
     * @return the decoded copy, or null when there was nothing to decode
     */
    private static String jsonUnescape(String aText) {
        int at = aText.indexOf("\\u");
        if (at < 0) {
            return null;
        }

        StringBuilder out = new StringBuilder(aText.length());
        for (int i = 0; i < aText.length(); ) {
            int value = i + 5 < aText.length() && aText.charAt(i) == '\\' && aText.charAt(i + 1) == 'u'
                    ? hexQuad(aText, i + 2)
                    : -1;
            if (value < 0) {
                out.append(aText.charAt(i));
                i++;
            } else {
                out.append((char) value);
                i += 6;
            }
        }
        return out.toString();
    }

    private static int hexQuad(String aText, int aFrom) {
        int value = 0;
        for (int i = aFrom; i < aFrom + 4; i++) {
            int digit = Character.digit(aText.charAt(i), 16);
            if (digit < 0) {
                return -1;
            }
            value = value * 16 + digit;
        }
        return value;
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
