package com.payneteasy.telegram.bot.client.http;

import org.junit.Test;

import static com.payneteasy.telegram.bot.client.http.TelegramLogScrubber.scrub;
import static com.payneteasy.telegram.bot.client.http.TelegramLogScrubber.scrubBody;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * : the scrubbing function itself. That it is actually applied is T2's job.
 */
public class TelegramLogScrubberTest {

    private static final String SECRET_PART = "AAHfake0Token1For2Tests3456789abcXYZ";
    private static final String TOKEN       = "123456789:" + SECRET_PART;
    private static final String OPAQUE      = "opaque-secret-value-1234";
    private static final String SECRET      = "5d41402abc4b2a76b9719d911017c592a1b2c3d4e5f60718293a4b5c6d7e8f90";

    @Test
    public void tokenInWebhookUrlIsRemovedAndBotIdKept() {
        String scrubbed = scrub("Sending POST to https://webhook.example.com/telegram/webhook?bot_token=" + TOKEN + " with 30000ms");

        assertFalse(scrubbed.contains(SECRET_PART));
        assertTrue(scrubbed.contains("bot_token=***"));
    }

    @Test
    public void tokenInWebhookUrlInsideJsonBodyIsRemoved() {
        String scrubbed = scrub("{\"url\":\"https://webhook.example.com/telegram/webhook?bot_token=" + TOKEN + "\"}");

        assertFalse(scrubbed.contains(SECRET_PART));
        assertTrue("the rest of the body survives", scrubbed.contains("https://webhook.example.com/telegram/webhook"));
    }

    @Test
    public void secretTokenValueIsRemovedByFieldName() {
        assertEquals("{\"url\":\"https://webhook.example.com/hook?bot_id=123456789\",\"secret_token\":\"***\"}",
                scrubBody("{\"url\":\"https://webhook.example.com/hook?bot_id=123456789\",\"secret_token\":\"" + SECRET + "\"}"));
    }

    @Test
    public void secretTokenValueIsRemovedInPrettyPrintedBody() {
        String scrubbed = scrubBody("{\n  \"url\": \"https://webhook.example.com/hook\",\n  \"secret_token\": \"" + SECRET + "\"\n}");

        assertFalse(scrubbed.contains(SECRET));
        assertTrue(scrubbed, scrubbed.contains("\"secret_token\":\"***\""));
    }

    @Test
    public void bareTokenIsMaskedAndBotIdKept() {
        assertEquals("Unauthorized for 123456789:***", scrub("Unauthorized for " + TOKEN));
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


    /**
     * Deep enough to pass the limit here, shallow enough that the JSON parser still goes through
     * it: newer Gson refuses a document nested past 255, and then this limit is never the thing
     * being tested.
     */
    @Test
    public void aDeepButParsableBodyIsMaskedBeyondTheDepthLimit() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            deep.append("{\"a\":");
        }
        deep.append("\"").append(SECRET).append("\"");
        for (int i = 0; i < 150; i++) {
            deep.append('}');
        }

        assertFalse(scrubBody(deep.toString()).contains(SECRET));
    }

    /**
     * A body that looks like JSON and does not parse is not printed at all. A secret_token is
     * identified by the name of its key and by nothing else, so without the structure there is
     * nothing dependable to look for — seven rounds of review were spent proving that by finding
     * another spelling each time.
     */
    @Test
    public void aBodyThatDoesNotParseIsWithheld() {
        for (String body : new String[] {
                "{\"secret_token\":\"" + OPAQUE,
                "{\"secret_token\":{\"a\":\"" + OPAQUE,
                "{\"secret_token\":\"secret_token\":\"" + OPAQUE + "\"}",
        }) {
            String scrubbed = scrubBody(body);
            assertFalse(scrubbed, scrubbed.contains(OPAQUE));
            assertTrue(scrubbed, scrubbed.contains("withheld"));
        }
    }

    /** The shape can be hidden by encoding, and a bare string is a whole document too. */
    @Test
    public void aBodyIsRecognisedAsJsonWhateverItsSpelling() {
        for (String body : new String[] {
                "%22secret_token%22%3A%22" + OPAQUE + "%22,%22next%22:%22keep%22",
                "%22secret_token%22:%7B\"a\":{},\"b\":\"" + OPAQUE + "\"%7D",
                "\"\\u0022secret_token\\u0022:\\u0022" + OPAQUE + "\\u0022\"",
        }) {
            assertFalse(body, scrubBody(body).contains(OPAQUE));
        }
    }

    /** A string whose contents are JSON is treated as a body, or its fields would be invisible. */
    @Test
    public void jsonInsideAStringIsTreatedAsABody() {
        assertFalse(scrubBody("{\"payload\":\"{\\\"secret_token\\\":\\\"" + OPAQUE + "\\\"}\"}").contains(OPAQUE));
    }

    @Test
    public void aBodyTooDeepForTheParserIsWithheldToo() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 100_000; i++) {
            deep.append('[');
        }
        deep.append("{\"secret_token\":{\"v\":\"").append(OPAQUE).append("\"}}");

        assertFalse(scrubBody(deep.toString()).contains(OPAQUE));
    }

    /** What still parses is still readable, and the field is still masked by its key. */
    @Test
    public void aBodyThatParsesKeepsItsDiagnosis() {
        assertEquals("{\"url\":\"https://webhook.example.com/hook\",\"secret_token\":\"***\"}",
                scrubBody("{\"url\":\"https://webhook.example.com/hook\",\"secret_token\":\"" + SECRET + "\"}"));

        assertFalse(scrubBody("{\"secret_token\":{\"v\":\"" + OPAQUE + "\"},\"next\":\"keep\"}").contains(OPAQUE));
        assertTrue(scrubBody("{\"secret_token\":{\"v\":\"" + OPAQUE + "\"},\"next\":\"keep\"}").contains("keep"));
    }

    /** Not JSON at all keeps going through the flat rules — that is what they are for. */
    @Test
    public void textThatIsNotJsonStillGoesThroughTheFlatRules() {
        assertEquals("Sending POST to https://h/hook?bot_token=*** with 30000ms",
                scrub("Sending POST to https://h/hook?bot_token=" + TOKEN + " with 30000ms"));
    }

    /**
     * Thousands of markers used to make each one rescan everything after it. A `?` cannot start a
     * second query, so it ends a value.
     */
    @Test
    public void aValueEndsAtTheNextQuestionMark() {
        assertEquals("https://h/?token=***?token=***", scrub("https://h/?token=a?token=" + OPAQUE));
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
