package com.payneteasy.telegram.bot.client.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
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
 * Mirrors the scrubber on the other side of the connection so that both ends of the connection hide the same
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

    /** Start of a sensitive query parameter, up to and including the equals sign. */
    private static final Pattern SENSITIVE_PARAM_MARKER = Pattern.compile("[?&](?:bot_token|secret_token|token)=");

    /** {@code <bot_id>:<secret>}, the same pattern the receiving side uses. */
    private static final Pattern TOKEN = Pattern.compile("([0-9]{1,20}):[A-Za-z0-9_-]{20,256}");

    private TelegramLogScrubber() {
    }

    /**
     * Scrub a response or request body. Parsing it undoes Gson's escaping in one move, so the
     * query-parameter rule works on a value that is a URL rather than on its escaped spelling.
     *
     * A body that does not parse — truncated, an HTML error page from an intermediary, or deeper
     * than the parser can take — falls back to the flat rules.
     */
    public static String scrubBody(String aBody) {
        return scrubBody(aBody, 0);
    }

    /**
     * Either it walks as an object or an array, or it is not logged. There is no guess about what
     * the body was meant to be: every guess so far has disagreed with the parser somewhere — the
     * first character, a byte order mark, a comment before the document — and each disagreement
     * was a body going to the flat rules, where a field name means nothing.
     *
     * The cost is that an HTML error page from an intermediary is withheld along with everything
     * else. That is the same trade the proxy at the other end of this connection makes, for the
     * same reason: a `secret_token` is identified by the name of its key and by nothing else, so
     * without the structure there is nothing dependable left to look for.
     */
    private static String scrubBody(String aBody, int aDepth) {
        if (aBody == null || aBody.isEmpty()) {
            return aBody;
        }
        if (aDepth > MAX_DEPTH) {
            return withheld(aBody);
        }

        JsonElement parsed = parseOrNull(aBody);
        return parsed == null ? withheld(aBody) : scrubJson(parsed, aDepth).toString();
    }

    /**
     * A {@code secret_token} is identified by the name of its key and by nothing else — the value
     * is 64 hex characters that match no pattern — so a body we could not walk has nothing
     * dependable left to look for. Seven rounds of review were spent proving that, each one
     * getting past the previous attempt with another spelling.
     */
    private static String withheld(String aBody) {
        return "<unparsable body, " + aBody.length() + " chars, withheld>";
    }

    /**
     * A string inside a body that walked.
     *
     * Three outcomes, and which one applies is decided by the parser wherever it can be: a string
     * that is an object or an array is walked like the body it is; a string that was written as one
     * and did not parse is withheld, exactly as the same text standing alone would be; anything
     * else is text, keeps its diagnosis and goes through the flat rules.
     *
     * The opening-brace test is a guess, and for a body it was the wrong tool — the parser accepts
     * things it does not predict. Here it is bounded: a wrong yes costs one field's text, where for
     * a body it decided whether a secret was printed.
     */
    public static String scrubFragment(String aValue) {
        return aValue == null || aValue.isEmpty() ? aValue : scrubStringValue(aValue, 0);
    }

    private static String scrubStringValue(String aValue, int aDepth) {
        // No depth guard of its own: every path below either masks, withholds, or hands the value
        // to scrubJson, which has one. A branch no test can tell apart is worse than no branch.
        JsonElement nested = parseOrNull(aValue);
        if (nested != null) {
            return scrubJson(nested, aDepth + 1).toString();
        }
        return writtenAsJson(aValue) ? withheld(aValue) : scrub(aValue);
    }

    /** Whether the text opens as an object or an array, encoding undone. */
    private static boolean writtenAsJson(String aText) {
        String decoded = Decoded.of(aText).text;
        for (int i = 0; i < decoded.length(); i++) {
            char c = decoded.charAt(i);
            if (!Character.isWhitespace(c) && c != '\ufeff') {
                return c == '{' || c == '[';
            }
        }
        return false;
    }

    /** @return the parsed object or array, or null when it is neither or will not parse */
    private static JsonElement parseOrNull(String aText) {
        try {
            JsonElement parsed = new JsonParser().parse(aText);
            return parsed.isJsonObject() || parsed.isJsonArray() ? parsed : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Scrub a flat string: a URL, an exception message, a body that did not parse.
     *
     * Two rules, neither of which has anything to do with JSON, which is why neither breaks on it:
     * the sensitive query parameters and the token pattern. A {@code secret_token} is not among
     * them — it is recognised by the name of its key, and a key is a thing structure has. Bodies
     * are handled by {@link #scrubBody(String)} and never arrive here.
     *
     * The marker is found in the decoded copy — that is what an encoded parameter name needs — and
     * where the value <em>ends</em> is decided by walking the original. Both halves matter: looking
     * for the end in the decoded copy is what let {@code ?token=%22opaque...} through, because the
     * {@code %22} the value was carrying turned into the very quote the search stops at.
     */
    public static String scrub(String aText) {
        if (aText == null || aText.isEmpty()) {
            return aText;
        }

        Decoded    decoded = Decoded.of(aText);
        List<Span> spans   = new ArrayList<Span>();

        collectParams(spans, aText, decoded);
        collectTokens(spans, aText, decoded);

        return apply(aText, spans);
    }

    /** {@code ?token=...}, up to where the query value ends in the original text. */
    private static void collectParams(List<Span> aSpans, String aOriginal, Decoded aDecoded) {
        Matcher matcher = SENSITIVE_PARAM_MARKER.matcher(aDecoded.text);
        int     scanned = 0;

        while (matcher.find()) {
            int from = aDecoded.origin[matcher.end()];
            // Markers come in order, and the last scan found no terminator before where it stopped,
            // so there is none to find there now either. Without this a string of thousands of
            // markers makes each one walk everything after it: 16 000 of them took two seconds.
            int end  = endOfQueryValue(aOriginal, Math.max(from, scanned));
            scanned  = end;
            aSpans.add(new Span(aDecoded.origin[matcher.start()], from, end, ""));
        }
    }

    /**
     * The bare token pattern, the backstop for shapes we did not foresee.
     *
     * The only rule whose end still comes from the decoded copy, and the only one where that is
     * safe: it has no terminator to be fooled by, only a positive character class, so a mapped
     * span can cover more of the original than the match did but never less.
     */
    private static void collectTokens(List<Span> aSpans, String aOriginal, Decoded aDecoded) {
        Matcher matcher = TOKEN.matcher(aDecoded.text);
        while (matcher.find()) {
            aSpans.add(new Span(aDecoded.origin[matcher.start()], aDecoded.origin[matcher.end(1)],
                    aDecoded.origin[matcher.end()], ":"));
        }
    }

    /**
     * Where a query parameter value ends in the original text.
     *
     * The two kinds of escape belong to different layers and end the value differently. A JSON
     * escape is Gson's spelling of a real character: {@code \u0026} <em>is</em> the ampersand that
     * separates the next parameter, so it ends the value. A percent escape belongs to the URL: the
     * {@code %26} in a value is data the value carries, and reading it as a separator is what used
     * to release everything after it.
     */
    private static int endOfQueryValue(String aText, int aFrom) {
        for (int i = aFrom; i < aText.length(); i++) {
            char c = aText.charAt(i);
            if (c == '"' || endsValue(c)) {
                return i;
            }
            if (c == '\\' && i + 1 < aText.length() && aText.charAt(i + 1) == '"') {
                // The quote that closes the JSON string the URL sits in.
                return i;
            }
            if (c == '\\' && i + 5 < aText.length() && aText.charAt(i + 1) == 'u') {
                int decoded = Decoded.hex(aText, i + 2, 4);
                if (decoded >= 0 && endsValue((char) decoded)) {
                    return i;
                }
                i += 5;
            }
        }
        return aText.length();
    }

    /** A {@code ?} is an ordinary character inside a query (RFC 3986 §3.4), so it ends nothing. */
    private static boolean endsValue(char aChar) {
        return aChar == '&' || aChar == '\'' || aChar == '<' || aChar == '>' || Character.isWhitespace(aChar);
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
            String value = aElement.getAsString();
            // A string whose contents are themselves JSON gets the same treatment as a body, or the
            // fields inside it would be invisible: the flat rules know a URL and a token, not a key.
            return new JsonPrimitive(scrubStringValue(value, aDepth));
        }
        return aElement;
    }

    /**
     * Replace every span, overlapping ones merged.
     *
     * Merging rather than discarding: two spans can overlap only partly — a query value running
     * into a {@code secret_token} field that starts inside it — and dropping the second would
     * leave the part of it that reaches further in the open. The union masks more than either,
     * which is the right way to be wrong here.
     */
    private static String apply(String aOriginal, List<Span> aSpans) {
        if (aSpans.isEmpty()) {
            return aOriginal;
        }

        Collections.sort(aSpans, new Comparator<Span>() {
            @Override
            public int compare(Span aLeft, Span aRight) {
                return aLeft.start != aRight.start ? aLeft.start - aRight.start : aRight.maskTo - aLeft.maskTo;
            }
        });

        List<Span> merged = new ArrayList<Span>();
        for (Span span : aSpans) {
            Span last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && span.start < last.maskTo) {
                if (span.maskTo > last.maskTo) {
                    merged.set(merged.size() - 1, new Span(last.start, last.maskFrom, span.maskTo, last.separator));
                }
            } else {
                merged.add(span);
            }
        }

        StringBuilder out = new StringBuilder(aOriginal);
        for (int i = merged.size() - 1; i >= 0; i--) {
            Span span = merged.get(i);
            out.replace(span.maskFrom, span.maskTo, span.separator + MASK);
        }
        return out.toString();
    }

    /** One match, in positions of the original string: where it starts, and what to replace. */
    private static final class Span {

        private final int    start;
        private final int    maskFrom;
        private final int    maskTo;
        private final String separator;

        private Span(int aStart, int aMaskFrom, int aMaskTo, String aSeparator) {
            start     = aStart;
            maskFrom  = aMaskFrom;
            maskTo    = aMaskTo;
            separator = aSeparator;
        }
    }

    /**
     * A decoded copy of a string, with the position in the original every character came from.
     *
     * Written by hand because neither {@link java.net.URLDecoder} nor any decoder in the
     * dependencies keeps that map — and the map is the whole point: it is what lets a rule read
     * the decoded form while the replacement lands in the original one.
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

        static int hex(String aText, int aFrom, int aLength) {
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
    }
}
