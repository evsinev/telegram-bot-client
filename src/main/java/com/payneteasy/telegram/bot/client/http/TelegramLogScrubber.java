package com.payneteasy.telegram.bot.client.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Removes anything secret-shaped from a string on its way into a log record or into an
 * exception message.
 *
 * The rule this class exists to enforce: a string that came from the network, or that
 * contains an assembled URL, reaches a log record or an exception message only through
 * {@link #scrub(String)} or {@link #scrubBody(String)}. Enumerating the places instead of
 * stating the rule has already proved to be an incomplete list twice.
 *
 * Mirrors {@code telegram-proxy/src/scrub.rs} so that both ends of the connection hide the same
 * things: {@link #scrubBody(String)} corresponds to its {@code scrub_body}, walking the body as
 * JSON, and {@link #scrub(String)} to its flat {@code scrub}.
 *
 * <h2>Only the secret is replaced</h2>
 *
 * Nothing here decodes the string it is given. An earlier attempt did — it compared the text with
 * its decoded copies and scrubbed whichever revealed the most — and that turned out to corrupt
 * exactly what a log needs: {@code %26} inside a value became a separator and let the rest of the
 * value through, a neighbouring parameter lost its value, and an escaped {@code \\u000a} came out
 * as a real line break, splitting the record in two. So the match happens in place: what is not a
 * secret comes out byte for byte as it went in.
 *
 * <h2>One layer of encoding</h2>
 *
 * A secret can be hidden from a rule by encoding rather than by shape, so every literal the
 * patterns are built from — a parameter name, a field name, a separator — is recognised in its
 * plain form and in one layer of encoding, character by character: {@code _} also as {@code %5F}
 * or {@code \\u005f}, {@code :} also as {@code %3A} or {@code \\u003a}. Values are never touched,
 * which is what keeps their boundaries intact.
 *
 * Two layers ({@code %255F}) are deliberately not supported. The bodies here come from Telegram
 * and from ourselves; anyone crafting one to smuggle a secret past this class would have to know
 * that secret already. This guards against printing our own token, not against a forged answer.
 */
public final class TelegramLogScrubber {

    private static final String MASK = "***";

    /** Field whose value is replaced wholesale — 64 hex characters that no pattern would catch. */
    private static final String SECRET_TOKEN_FIELD_NAME = "secret_token";

    /**
     * Deeper than this and the subtree is masked wholesale. No Telegram body is anywhere near it,
     * and a walk that recurses as far as the input asks is a way to lose the walk itself.
     */
    private static final int MAX_DEPTH = 100;

    /** One literal backslash, as a regular expression. */
    private static final String BACKSLASH = "\\\\";

    /**
     * Value of the {@code secret_token} field, in a body that did not go through the parser. The
     * closing quote is not part of the match, so a truncated body — the reason this string did not
     * parse in the first place — is covered as well.
     */
    private static final Pattern SECRET_TOKEN_FIELD = Pattern.compile(
            "(" + BACKSLASH + "?\"" + anyEncodingOf("secret_token") + BACKSLASH + "?\"\\s*:\\s*" + BACKSLASH + "?\")[^\"" + BACKSLASH + "]*");

    /**
     * Value of a sensitive query parameter, ending where the query does. A separator is a real
     * one: {@code ?}, {@code &}, or the {@code &} Gson escapes into {@code \\u0026}. An encoded
     * {@code %26} is by definition inside a value and stays there.
     */
    private static final Pattern SENSITIVE_PARAM = Pattern.compile(
            "((?:\\?|&|" + BACKSLASH + "u0026)(?:" + anyEncodingOf("bot_token") + "|" + anyEncodingOf("secret_token") + "|" + anyEncodingOf("token") + ")"
                    + anyEncodingOf("=") + ")[^&\\s\"'<>]*");

    /** {@code <bot_id>:<secret>}, taken from {@code scrub.rs:29-30}. */
    private static final Pattern TOKEN = Pattern.compile(
            "([0-9]{1,20})" + anyEncodingOf(":") + "[A-Za-z0-9_-]{20,256}");

    private TelegramLogScrubber() {
    }

    /**
     * Scrub a response or request body. Parsing it undoes Gson's escaping in one move, so the
     * query-parameter rule works on a value that is a URL rather than on its escaped spelling.
     *
     * A body that does not parse — truncated, an HTML error page from an intermediary, or simply
     * deeper than the parser can take — falls back to the flat rules, which recognise the escaped
     * spellings themselves.
     */
    public static String scrubBody(String aBody) {
        if (aBody == null || aBody.isEmpty()) {
            return aBody;
        }
        if (looksLikeJson(aBody)) {
            try {
                return scrubJson(new JsonParser().parse(aBody), 0).toString();
            } catch (RuntimeException e) {
                // see above - the flat rules still apply
            }
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
        String text = SECRET_TOKEN_FIELD.matcher(aText).replaceAll("$1" + MASK);
        text = SENSITIVE_PARAM.matcher(text).replaceAll("$1" + MASK);
        return TOKEN.matcher(text).replaceAll("$1:" + MASK);
    }

    /**
     * A body is taken through the parser only when it opens as an object or an array. Anything
     * else keeps its exact shape — run through the parser, a one-word error page would come back
     * quoted — and is handled by the flat rules. A bare JSON string is a whole document too, but
     * the flat rules cover it, so it does not earn a branch of its own.
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
     * Build the part of a pattern that matches a literal written plainly, percent-encoded, or
     * escaped the way Gson escapes it — per character, one layer deep.
     *
     * Generated rather than written out so that the supported forms live in one place: spelled by
     * hand, {@code secret_token} alone would be a hundred characters of alternation that the next
     * reader has to verify letter by letter.
     */
    private static String anyEncodingOf(String aLiteral) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < aLiteral.length(); i++) {
            char   c   = aLiteral.charAt(i);
            String hex = caseInsensitiveHex(c);
            out.append("(?:").append(Pattern.quote(String.valueOf(c)))
               .append("|%").append(hex)
               .append('|').append(BACKSLASH).append("u00").append(hex)
               .append(')');
        }
        return out.toString();
    }

    /** {@code _} becomes {@code 5[fF]}: a hex pair whose letters match in either case. */
    private static String caseInsensitiveHex(char aChar) {
        StringBuilder out = new StringBuilder(8);
        for (char digit : String.format("%02x", (int) aChar).toCharArray()) {
            if (Character.isDigit(digit)) {
                out.append(digit);
            } else {
                out.append('[').append(digit).append(Character.toUpperCase(digit)).append(']');
            }
        }
        return out.toString();
    }
}
