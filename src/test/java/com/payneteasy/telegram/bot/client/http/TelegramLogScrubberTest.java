package com.payneteasy.telegram.bot.client.http;

import org.junit.Test;

import static com.payneteasy.telegram.bot.client.http.TelegramLogScrubber.scrub;
import static com.payneteasy.telegram.bot.client.http.TelegramLogScrubber.scrubBody;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * S1.3: the scrubbing function itself. That it is actually applied is T2's job.
 */
public class TelegramLogScrubberTest {

    private static final String SECRET_PART = "AAHfake0Token1For2Tests3456789abcXYZ";
    private static final String TOKEN       = "123456789:" + SECRET_PART;
    private static final String OPAQUE      = "opaque-secret-value-1234";
    private static final String SECRET      = "5d41402abc4b2a76b9719d911017c592a1b2c3d4e5f60718293a4b5c6d7e8f90";

    @Test
    public void tokenInWebhookUrlIsRemovedAndBotIdKept() {
        String scrubbed = scrub("Sending POST to https://gate.pne.io/telegram/webhook?bot_token=" + TOKEN + " with 30000ms");

        assertFalse(scrubbed.contains(SECRET_PART));
        assertTrue(scrubbed.contains("bot_token=***"));
    }

    @Test
    public void tokenInWebhookUrlInsideJsonBodyIsRemoved() {
        String scrubbed = scrub("{\"url\":\"https://gate.pne.io/telegram/webhook?bot_token=" + TOKEN + "\"}");

        assertFalse(scrubbed.contains(SECRET_PART));
        assertTrue("the rest of the body survives", scrubbed.contains("https://gate.pne.io/telegram/webhook"));
    }

    @Test
    public void secretTokenValueIsRemovedByFieldName() {
        assertEquals("{\"url\":\"https://gate.pne.io/hook?bot_id=123456789\",\"secret_token\":\"***\"}",
                scrub("{\"url\":\"https://gate.pne.io/hook?bot_id=123456789\",\"secret_token\":\"" + SECRET + "\"}"));
    }

    @Test
    public void secretTokenValueIsRemovedInPrettyPrintedBody() {
        String scrubbed = scrub("{\n  \"url\": \"https://gate.pne.io/hook\",\n  \"secret_token\": \"" + SECRET + "\"\n}");

        assertFalse(scrubbed.contains(SECRET));
        assertTrue(scrubbed.contains("\"secret_token\": \"***\""));
    }

    @Test
    public void bareTokenIsMaskedAndBotIdKept() {
        assertEquals("Unauthorized for 123456789:***", scrub("Unauthorized for " + TOKEN));
    }

    /**
     * Gson escapes {@code =} by default, so this is the form the parameter actually takes inside
     * a logged request body.
     */
    @Test
    public void escapedSeparatorInASerializedBodyStillHidesTheParameterValue() {
        String scrubbed = scrub("{\"url\":\"https://gate.pne.io/hook?secret_token\\u003d" + SECRET + "\"}");

        assertFalse(scrubbed.contains(SECRET));
        assertTrue(scrubbed.contains("secret_token\\u003d***"));
    }

    @Test
    public void percentEncodedSeparatorDoesNotHideTheToken() {
        String scrubbed = scrub("https://gate.pne.io/hook?x=123456789%3A" + SECRET_PART);

        assertFalse(scrubbed.contains(SECRET_PART));
    }

    /**
     * The four leaks the review of stage 1 reproduced. Each one used to come out in full.
     */
    @Test
    public void anEncodedTokenNextToAPlainOneIsNotLeftBehind() {
        String scrubbed = scrub(TOKEN + " encoded=123456789%3A" + SECRET_PART);

        assertFalse(scrubbed.contains(SECRET_PART));
    }

    @Test
    public void aStrayPercentDoesNotCostUsTheRestOfTheString() {
        String scrubbed = scrub("100% done 123456789%3A" + SECRET_PART);

        assertFalse(scrubbed.contains(SECRET_PART));
    }

    @Test
    public void secondQueryParameterOfASerializedBodyIsScrubbed() {
        String body = "{\"url\":\"https://h/hook?x\\u003d1\\u0026secret_token\\u003d" + SECRET + "\"}";

        assertFalse(scrubBody(body).contains(SECRET));
        assertFalse("and the flat fallback must hold it too", scrub(body).contains(SECRET));
    }

    @Test
    public void bodyIsWalkedAsJsonSoEscapingCannotHideAField() {
        String body = "{\"secret_\\u0074oken\":\"" + SECRET + "\",\"url\":\"https://h/hook?bot_token\\u003d" + TOKEN + "\"}";

        String scrubbed = scrubBody(body);

        assertFalse(scrubbed.contains(SECRET));
        assertFalse(scrubbed.contains(SECRET_PART));
        assertTrue("the readable part survives", scrubbed.contains("https://h/hook"));
    }

    @Test
    public void aBodyThatDoesNotParseFallsBackToTheFlatRules() {
        String truncated = "{\"url\":\"https://h/hook?bot_token=" + TOKEN;

        assertFalse(scrubBody(truncated).contains(SECRET_PART));
    }

    /** An encoded separator is recognised where it stands; the text around it is not rewritten. */
    @Test
    public void anEncodedSeparatorIsMaskedWithoutTouchingTheRestOfTheLine() {
        assertEquals("a+b 123456789:***", scrub("a+b 123456789%3A" + SECRET_PART));
    }

    /**
     * The round-3 regression: decoding the whole string made {@code %26} inside a value look like
     * a separator, and the rest of the value came out in the open.
     */
    @Test
    public void anEncodedAmpersandStaysInsideTheValueItBelongsTo() {
        assertEquals("https://h/?bot%5Ftoken=***", scrub("https://h/?bot%5Ftoken=%26" + OPAQUE));
    }

    /** Whatever is not a secret has to come out exactly as it went in. */
    @Test
    public void nothingButTheSecretIsRewritten() {
        assertEquals("https://h/?x=%26token=public&bot%5Ftoken=***",
                scrub("https://h/?x=%26token=public&bot%5Ftoken=" + OPAQUE));
    }

    /**
     * An escaped line break must stay escaped: a scrubber that turns it into a real one splits the
     * log record in two, which is the opposite of what this class is for.
     */
    @Test
    public void anEscapedLineBreakIsNotTurnedIntoARealOne() {
        String scrubbed = scrubBody("\"before\\u000aFORGED 123456789\\u003a" + SECRET_PART + "\"");

        assertFalse(scrubbed.contains(SECRET_PART));
        assertFalse("the record must stay one line", scrubbed.contains("\n"));
    }

    /** A truncated body is exactly the body that did not survive the parser. */
    @Test
    public void anUnterminatedSecretValueIsStillMasked() {
        assertEquals("{\"secret_token\":\"***", scrubBody("{\"secret_token\":\"" + OPAQUE));
    }

    /** A JSON string holding JSON: the field name is spelled with escaped quotes. */
    @Test
    public void aSecretInsideAStringThatHoldsJsonIsMasked() {
        assertFalse(scrubBody("\"{\\\"secret_token\\\":\\\"" + OPAQUE + "\\\"}\"").contains(OPAQUE));
    }

    /**
     * A token sits inside the parameter value that holds it, and the value reaches further than
     * the token does. The whole value has to go: masking the token alone leaves its frame behind,
     * and replacing both in turn works on offsets the first replacement already invalidated.
     */
    @Test
    public void theWholeParameterValueGoesEvenWhenATokenSitsInsideIt() {
        assertEquals("https://h/?bot_token=***&next=keepme",
                scrub("https://h/?bot_token=" + TOKEN + "&next=keepme"));
    }

    /** Round 4: the secret's own characters encoded, not just the separator. */
    @Test
    public void anEncodedCharacterInsideTheSecretDoesNotHideIt() {
        assertEquals("123456789:***", scrub("123456789%3A%41" + SECRET_PART.substring(1)));
    }

    @Test
    public void aStructuredSecretTokenValueIsMaskedWhole() {
        assertFalse(scrubBody("{\"secret_token\":{\"value\":\"" + OPAQUE + "\"}}").contains(OPAQUE));
    }

    /** A body too deep for the parser lands in the flat rules — with a structured value. */
    @Test
    public void aStructuredSecretSurvivesTheParserGivingUp() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 100_000; i++) {
            deep.append('[');
        }
        deep.append("{\"secret_token\":{\"value\":\"").append(OPAQUE).append("\"}}");
        for (int i = 0; i < 100_000; i++) {
            deep.append(']');
        }

        assertFalse(scrubBody(deep.toString()).contains(OPAQUE));
    }

    @Test
    public void anEscapeInsideTheValueDoesNotCutTheMaskShort() {
        assertEquals("{\"secret_token\":\"***", scrubBody("{\"secret_token\":\"\\u006f" + OPAQUE.substring(1)));
    }

    /** The neighbour of a masked parameter has to survive untouched. */
    @Test
    public void theParameterAfterAMaskedOneIsKept() {
        assertEquals("{\"url\":\"https://h/?token=***\\u0026x\\u003dpublic\"}",
                scrub("{\"url\":\"https://h/?token=secret\\u0026x\\u003dpublic\"}"));
    }

    /** An encoded '=' is part of the name, so this parameter is not called "token" at all. */
    @Test
    public void anEncodedEqualsIsNotAStructuralSeparator() {
        assertEquals("https://h/?token%3Dpublic=value", scrub("https://h/?token%3Dpublic=value"));
    }

    /**
     * The declared boundary: one layer of encoding, not two. Written down as a test so that it
     * reads as a decision rather than as something nobody got round to.
     */
    @Test
    public void twoLayersOfEncodingAreOutOfScope() {
        assertTrue("if this ever starts passing, the boundary in the plan moved and nobody said so",
                scrub("https://h/?bot%255Ftoken=" + OPAQUE).contains(OPAQUE));
    }

    @Test
    public void aMalformedEscapeDoesNotRewriteTheRecord() {
        assertEquals("a+b c%ZZ", scrubBody("a+b c%ZZ"));
    }

    /**
     * Round 5: an encoded delimiter inside the value used to end it, because the search for the
     * end ran on the decoded copy where {@code %22} had become the very quote it stops at.
     */
    @Test
    public void anEncodedDelimiterInsideAValueDoesNotEndIt() {
        assertEquals("https://h/?token=***&next=ok", scrub("https://h/?token=%22" + OPAQUE + "&next=ok"));
        assertEquals("https://h/?token=***&next=ok", scrub("https://h/?token=%20" + OPAQUE + "&next=ok"));
    }

    /** An escaped quote is content of the string, not its end. */
    @Test
    public void anEscapedQuoteInsideTheValueDoesNotEndIt() {
        assertEquals("{\"secret_token\":\"***", scrubBody("{\"secret_token\":\"\\\"" + OPAQUE));
    }

    /**
     * A quote written as \u0022 is content too: the scan must not close the string on it.
     *
     * Through {@link TelegramLogScrubber#scrub}, not {@code scrubBody}: a body that parses never
     * reaches this scanner at all, and a test that goes through the parser proves nothing about it.
     */
    @Test
    public void anEscapedQuoteInsideAStructureDoesNotCloseIt() {
        assertFalse(scrubBody("{\"secret_token\":{\"x\":\"\\u0022}" + OPAQUE + "\"}}").contains(OPAQUE));

        // Exact output, so that masking too much is caught as well as masking too little: a brace
        // inside a string must not end the structure, and the field after it must survive.
        assertEquals("{\"secret_token\":***,\"next\":\"keep\"}",
                scrub("{\"secret_token\":{\"x\":\"}\"},\"next\":\"keep\"}"));
    }

    /** Brackets match by kind: a '[' is not closed by a '}'. */
    @Test
    public void aBracketIsNotClosedByTheWrongKind() {
        assertFalse(scrubBody("{\"secret_token\":[}" + OPAQUE + "]}").contains(OPAQUE));
    }

    /** The field after a masked structure has to survive — the scan must find its real end. */
    @Test
    public void theFieldAfterAMaskedStructureIsKept() {
        assertEquals("{\"secret_token\":\"***\",\"next\":\"keep\"}",
                scrubBody("{\"secret_token\":{\"a\":1},\"next\":\"keep\"}"));

        // And on the flat path, where the end of the structure is found by scanning rather than
        // by the parser.
        assertEquals("{\"secret_token\":***,\"next\":\"keep\"}",
                scrub("{\"secret_token\":{\"a\":1},\"next\":\"keep\"}"));
    }

    /**
     * Two spans can overlap only partly, and dropping the second used to leave the part of it that
     * reached further in the open.
     */
    @Test
    public void partiallyOverlappingSpansAreMergedNotDropped() {
        assertFalse(scrub("?token=a\\\"secret_token\\\":\\\"" + OPAQUE + "\\\"").contains(OPAQUE));
    }

    /** The three ways the second review got a secret past the structural walk. */
    @Test
    public void percentEncodedParameterNameDoesNotHideTheValue() {
        String body = "{\"url\":\"https://h/h?bot%5Ftoken=" + OPAQUE + "\"}";

        assertFalse(scrubBody(body).contains(OPAQUE));
    }

    @Test
    public void aBareJsonStringIsAWholeDocumentToo() {
        assertFalse(scrubBody("\"123456789\\u003a" + SECRET_PART + "\"").contains(SECRET_PART));
    }

    @Test
    public void aBodyTooDeepForTheParserStillGetsScrubbed() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 100_000; i++) {
            deep.append('[');
        }
        deep.append("{\"secret_\\u0074oken\":\"").append(OPAQUE).append("\"}");
        for (int i = 0; i < 100_000; i++) {
            deep.append(']');
        }

        assertFalse(scrubBody(deep.toString()).contains(OPAQUE));
    }

    @Test
    public void aDeepButParsableBodyIsMaskedBeyondTheDepthLimit() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            deep.append("{\"a\":");
        }
        deep.append("\"").append(SECRET).append("\"");
        for (int i = 0; i < 300; i++) {
            deep.append('}');
        }

        assertFalse(scrubBody(deep.toString()).contains(SECRET));
    }

    @Test
    public void ordinaryTextIsUnchanged() {
        assertEquals("1 sendMessage: response {\"ok\":true}", scrub("1 sendMessage: response {\"ok\":true}"));
        assertEquals("chat 123456789 replied at 12:30", scrub("chat 123456789 replied at 12:30"));
    }

    @Test
    public void nullAndEmptyAreCarriedThrough() {
        assertEquals(null, scrub(null));
        assertEquals("", scrub(""));
    }
}
