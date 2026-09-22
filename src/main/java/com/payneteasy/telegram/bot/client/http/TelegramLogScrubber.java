package com.payneteasy.telegram.bot.client.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
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
 * <h2>Decode to find, mask in the original</h2>
 *
 * A secret can be hidden from a rule by encoding rather than by shape, so the rules run against a
 * decoded copy. What comes out, though, is the original string with only the matched spans
 * replaced — the copy carries a map back to where each character came from. Two earlier attempts
 * each gave up one half of this and each was wrong for it: scrubbing the decoded copy itself
 * turned {@code %26} inside a value into a separator and released the rest of it, dropped a
 * neighbouring parameter, and turned an escaped {@code \\u000a} into a real line break that split
 * the record in two; matching only literal spellings stopped seeing a secret whose own characters
 * were encoded ({@code 123456789%3A%41AH...}).
 *
 * <h2>What is decoded</h2>
 *
 * JSON escapes ({@code \\u0026}) always: those are Gson's spelling of a real character. Percent
 * escapes too, except for the structural delimiters {@code %3D}, {@code %26} and {@code %3F} — an
 * encoded delimiter is by definition data inside a value, not structure, and treating it as
 * structure is what let the tail of a value through.
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

    /** Structural delimiters, which stay encoded so that an encoded one is read as data. */
    private static final Pattern KEPT_ENCODED = Pattern.compile("%(?:3[dDfF]|26)");

    /**
     * Value of the {@code secret_token} field, up to the closing quote. The quotes may each carry
     * a backslash: a JSON string whose content is itself JSON spells them {@code \"} — a whole
     * body can arrive that way.
     */
    private static final Pattern SECRET_TOKEN_FIELD = Pattern.compile("(\\\\?\"secret_token\\\\?\"\\s*:\\s*\\\\?\")[^\"]*");

    /** The same field with a value that is not a string — an object or an array follows. */
    private static final Pattern SECRET_TOKEN_STRUCTURE = Pattern.compile("\\\\?\"secret_token\\\\?\"\\s*:\\s*(?=[\\[{])");

    /** Value of a sensitive query parameter, ending where the query does. */
    private static final Pattern SENSITIVE_PARAM = Pattern.compile("([?&](?:bot_token|secret_token|token)=)[^&\\s\"'<>]*");

    /** {@code <bot_id>:<secret>}, taken from {@code scrub.rs:29-30}. */
    private static final Pattern TOKEN = Pattern.compile("([0-9]{1,20}):[A-Za-z0-9_-]{20,256}");

    private TelegramLogScrubber() {
    }

    /**
     * Scrub a response or request body. Parsing it undoes Gson's escaping in one move, so the
     * query-parameter rule works on a value that is a URL rather than on its escaped spelling.
     *
     * A body that does not parse — truncated, an HTML error page from an intermediary, or deeper
     * than the parser can take — falls back to the flat rules, which decode for themselves.
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
     * Scrub a flat string: a URL, an exception message, a body that did not parse.
     *
     * Four rules, in this order: the {@code secret_token} field with a string value and with a
     * structured one, the sensitive query parameters, and the token pattern as a backstop for
     * formats we did not foresee. The {@code bot_id} before the colon is kept, so a log line still
     * says which bot it is about.
     */
    public static String scrub(String aText) {
        if (aText == null || aText.isEmpty()) {
            return aText;
        }

        Decoded     decoded = Decoded.of(aText);
        List<Span>  spans   = new ArrayList<Span>();

        collect(spans, decoded, SECRET_TOKEN_FIELD, 1);
        collectStructures(spans, decoded);
        collect(spans, decoded, SENSITIVE_PARAM, 1);
        collect(spans, decoded, TOKEN, 1, ":");

        return decoded.apply(aText, spans);
    }

    private static void collect(List<Span> aSpans, Decoded aDecoded, Pattern aPattern, int aKeptGroup) {
        collect(aSpans, aDecoded, aPattern, aKeptGroup, "");
    }

    /**
     * Every rule keeps a prefix and masks the rest: the field name and its quote, the parameter
     * name, or the bot id. The prefix is taken from the original string, so a name that was
     * written {@code bot%5Ftoken} comes back out spelled the way it arrived.
     */
    private static void collect(List<Span> aSpans, Decoded aDecoded, Pattern aPattern, int aKeptGroup, String aSeparator) {
        Matcher matcher = aPattern.matcher(aDecoded.text);
        while (matcher.find()) {
            aSpans.add(new Span(matcher.start(), matcher.end(), matcher.end(aKeptGroup), aSeparator));
        }
    }

    /**
     * {@code "secret_token": {...}} — a body too deep for the parser lands in the flat rules, and
     * there a structured value used to pass untouched. Masked to the matching bracket, or to the
     * end when the body was cut before it.
     */
    private static void collectStructures(List<Span> aSpans, Decoded aDecoded) {
        Matcher matcher = SECRET_TOKEN_STRUCTURE.matcher(aDecoded.text);
        while (matcher.find()) {
            aSpans.add(new Span(matcher.start(), balancedEnd(aDecoded.text, matcher.end()), matcher.end(), ""));
        }
    }

    private static int balancedEnd(String aText, int aFrom) {
        int     depth    = 0;
        boolean inString = false;

        for (int i = aFrom; i < aText.length(); i++) {
            char c = aText.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return aText.length();
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

    /** One match: what to keep, what to replace, in positions of the decoded copy. */
    private static final class Span {

        private final int    start;
        private final int    end;
        private final int    keptUntil;
        private final String separator;

        private Span(int aStart, int aEnd, int aKeptUntil, String aSeparator) {
            start     = aStart;
            end       = aEnd;
            keptUntil = aKeptUntil;
            separator = aSeparator;
        }
    }

    /**
     * A decoded copy of a string, with the position in the original every character came from.
     *
     * Written by hand because neither {@link java.net.URLDecoder} nor any decoder in the
     * dependencies keeps that map — and the map is the whole point: it is what lets the rules read
     * the decoded form while the output stays the original one. {@code URLDecoder} would also be
     * wrong on both counts that matter here, throwing on a stray {@code %} and turning {@code +}
     * into a space.
     */
    private static final class Decoded {

        private final String text;
        private final int[]  origin;

        private Decoded(String aText, int[] aOrigin) {
            text   = aText;
            origin = aOrigin;
        }

        private static Decoded of(String aText) {
            StringBuilder out    = new StringBuilder(aText.length());
            int[]         origin = new int[aText.length() + 1];

            for (int i = 0; i < aText.length(); ) {
                int decoded = decodeAt(aText, i);
                int width   = decoded < 0 ? 1 : (aText.charAt(i) == '%' ? 3 : 6);

                origin[out.length()] = i;
                out.append(decoded < 0 ? aText.charAt(i) : (char) decoded);
                i += width;
            }
            origin[out.length()] = aText.length();

            return new Decoded(out.toString(), origin);
        }

        /** @return the decoded character at this position, or -1 when there is no escape here */
        private static int decodeAt(String aText, int aAt) {
            char c = aText.charAt(aAt);
            if (c == '%' && aAt + 2 < aText.length() && !KEPT_ENCODED.matcher(aText).region(aAt, aAt + 3).matches()) {
                return hex(aText, aAt + 1, 2);
            }
            if (c == '\\' && aAt + 5 < aText.length() && aText.charAt(aAt + 1) == 'u') {
                return hex(aText, aAt + 2, 4);
            }
            return -1;
        }

        private static int hex(String aText, int aFrom, int aLength) {
            int value = 0;
            for (int i = aFrom; i < aFrom + aLength; i++) {
                int digit = Character.digit(aText.charAt(i), 16);
                if (digit < 0) {
                    return -1;
                }
                value = value * 16 + digit;
            }
            return value;
        }

        /**
         * Replace every span in the original string.
         *
         * Spans overlap by design — a token sits inside the parameter value that holds it — and the
         * wider one has to win: masking the token alone would leave {@code bot_token=123:***} where
         * the whole value should have gone. So the earliest and longest span is taken and anything
         * inside it dropped, and only then are the survivors applied right to left, where earlier
         * offsets are still valid.
         */
        private String apply(String aOriginal, List<Span> aSpans) {
            if (aSpans.isEmpty()) {
                return aOriginal;
            }

            java.util.Collections.sort(aSpans, new java.util.Comparator<Span>() {
                @Override
                public int compare(Span aLeft, Span aRight) {
                    return aLeft.start != aRight.start ? aLeft.start - aRight.start : aRight.end - aLeft.end;
                }
            });

            List<Span> accepted = new ArrayList<Span>();
            int        coveredTo = -1;
            for (Span span : aSpans) {
                if (span.start >= coveredTo) {
                    accepted.add(span);
                    coveredTo = span.end;
                }
            }

            StringBuilder out = new StringBuilder(aOriginal);
            for (int i = accepted.size() - 1; i >= 0; i--) {
                Span span = accepted.get(i);
                out.replace(origin[span.keptUntil], origin[span.end], span.separator + MASK);
            }
            return out.toString();
        }
    }
}
