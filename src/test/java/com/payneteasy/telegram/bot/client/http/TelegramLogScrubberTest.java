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

    /**
     * Decoding is only ever a way to see a secret, never a way to rewrite the record: a token had
     * to be present for the decoded copy to be chosen at all, which is what makes this sensitive
     * to a decoder that maps {@code +} to a space.
     */
    @Test
    public void plusSignSurvivesEvenWhenACopyHadToBeDecoded() {
        String scrubbed = scrub("a+b 123456789%3A" + SECRET_PART);

        assertFalse(scrubbed.contains(SECRET_PART));
        assertEquals("a+b 123456789:***", scrubbed);
    }

    @Test
    public void aMalformedEscapeDoesNotRewriteTheRecord() {
        assertEquals("a+b c%ZZ", scrubBody("a+b c%ZZ"));
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
