package com.payneteasy.telegram.bot.client.http;

import org.junit.Test;

import static com.payneteasy.telegram.bot.client.http.TelegramLogScrubber.scrub;
import static com.payneteasy.telegram.bot.client.http.TelegramLogScrubber.scrubBody;
import static com.payneteasy.telegram.bot.client.http.TelegramLogScrubber.scrubFragment;
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

    /**
     * What a body is, is the parser's answer. It is lenient — a byte order mark, a {@code )]}'}
     * guard, a comment before the document — and guessing from the first character disagreed with
     * it, sending a body it would have parsed to the flat rules where a field name means nothing.
     */
    @Test
    public void whatCountsAsJsonIsWhatTheParserAccepts() {
        for (String prefix : new String[] { "\ufeff", ")]}'\n", "/*prefix*/" }) {
            String body = prefix + "{\"secret_token\":\"" + OPAQUE + "\"}";
            assertFalse(body, scrubBody(body).contains(OPAQUE));
        }
    }

    /**
     * No guess about what the body was meant to be: every guess so far disagreed with the parser
     * somewhere, and each disagreement was a body going to the flat rules where a field name means
     * nothing. It walks as an object or an array, or it is not logged.
     */
    @Test
    public void aBodyThatDoesNotWalkIsNeverLogged() {
        for (String body : new String[] {
                "/*prefix*/{\"secret_token\":\"" + OPAQUE,
                "\ufeff{\"secret_token\":\"" + OPAQUE,
                ")]}'\n{\"secret_token\":\"" + OPAQUE,
                "<html>" + OPAQUE + "</html>",
        }) {
            String scrubbed = scrubBody(body);
            assertFalse(scrubbed, scrubbed.contains(OPAQUE));
            assertTrue(scrubbed, scrubbed.contains("withheld"));
        }
    }

    /** A string field written as JSON gets the same treatment standing alone or nested. */
    @Test
    public void truncatedJsonInAStringFieldIsWithheldLikeABody() {
        String body = "{\"payload\":\"{\\\"secret_token\\\":\\\"" + OPAQUE + "\\\"\"}";

        assertFalse(body, scrubBody(body).contains(OPAQUE));
    }

    /** At the limit the value is masked, not handed to rules that cannot see a key. */
    @Test
    public void aStringAtTheDepthLimitIsMaskedRatherThanFlattened() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            deep.append('[');
        }
        deep.append("\"{\\\"secret_token\\\":\\\"").append(OPAQUE).append("\\\"}\"");
        for (int i = 0; i < 100; i++) {
            deep.append(']');
        }

        assertFalse(scrubBody(deep.toString()).contains(OPAQUE));
    }

    /** A description is a piece of the body, so a structure put there is walked, not flattened. */
    @Test
    public void aFragmentIsWalkedWhenItIsAStructureAndKeptWhenItIsProse() {
        assertFalse(scrubFragment("{\"secret_token\":\"" + OPAQUE + "\"}").contains(OPAQUE));
        assertEquals("Bad Request: chat not found", scrubFragment("Bad Request: chat not found"));
    }

    /**
     * A fragment the parser does not take, but which names the secret field, is withheld whatever
     * comes before it. Deciding by the first character let each of these out as prose.
     */
    @Test
    public void aFragmentThatDoesNotWalkButNamesTheSecretIsWithheld() {
        for (String fragment : new String[] {
                "/*prefix*/{\"secret_token\":\"" + OPAQUE + "\"",
                ")]}'\n{\"secret_token\":\"" + OPAQUE + "\"",
                "%EF%BB%BF%7B%22secret_token%22:%22" + OPAQUE + "%22%7D",
                "note: secret%5Ftoken=" + OPAQUE,
                "SECRET_TOKEN: " + OPAQUE,
        }) {
            assertEquals(fragment, "<unparsable body, " + fragment.length() + " chars, withheld>", scrubFragment(fragment));
        }
    }

    /** The same fragments inside a body that walks: the DEBUG record of the response. */
    @Test
    public void aStringFieldThatDoesNotWalkButNamesTheSecretIsWithheld() {
        for (String description : new String[] {
                "/*prefix*/{\\\"secret_token\\\":\\\"" + OPAQUE + "\\\"",
                "%EF%BB%BF%7B%22secret_token%22:%22" + OPAQUE + "%22%7D",
        }) {
            String body = "{\"ok\":false,\"error_code\":400,\"description\":\"" + description + "\"}";
            String scrubbed = scrubBody(body);
            assertFalse(scrubbed, scrubbed.contains(OPAQUE));
            assertTrue("the rest of the body stays readable: " + scrubbed, scrubbed.contains("\"error_code\":400"));
        }
    }

    /** A string that is merely text keeps its diagnosis rather than being withheld. */
    @Test
    public void aStringThatIsNotJsonKeepsItsText() {
        assertEquals("{\"description\":\"Bad Request: chat not found\"}",
                scrubBody("{\"description\":\"Bad Request: chat not found\"}"));
    }

    /**
     * The lenient parser hands some strings back unchanged. Feeding such a body to itself is a
     * StackOverflowError, which is an Error and escapes every handler meant to catch it.
     */
    @Test
    public void aBodyIsNeverHandedToItself() {
        for (String body : new String[] { "%22foo%22", "%7B%7D", "%22secret_token%22%3A%22" + OPAQUE + "%22" }) {
            assertEquals(body, "<unparsable body, " + body.length() + " chars, withheld>", scrubBody(body));
        }
    }

    /** One budget for the whole walk, strings inside strings included. */
    @Test
    public void nestingPastTheLimitIsMaskedAtTheLimit() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            deep.append("{\"a\":");
        }
        deep.append('"').append(SECRET).append('"');
        for (int i = 0; i < 101; i++) {
            deep.append('}');
        }

        assertFalse(scrubBody(deep.toString()).contains(SECRET));
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
     * A {@code ?} is an ordinary character inside a query (RFC 3986 §3.4), so the value runs past
     * it to the next real delimiter. An earlier version ended the value there to keep thousands of
     * markers from each rescanning the rest, and let the tail of this one out.
     */
    @Test
    public void aQuestionMarkInsideAValueDoesNotEndIt() {
        assertEquals("https://h/?token=***&x=keep", scrub("https://h/?token=foo?" + OPAQUE + "&x=keep"));
    }

    /** An apostrophe is one of the sub-delims a query may carry (RFC 3986 §3.4), not its end. */
    @Test
    public void anApostropheInsideAValueDoesNotEndIt() {
        assertEquals("https://h/?token=***&x=keep", scrub("https://h/?token=foo'" + OPAQUE + "&x=keep"));
        assertEquals("https://h/?secret_token=***&x=keep", scrub("https://h/?secret_token=foo'" + OPAQUE + "&x=keep"));
    }

    /** Gson's spelling of the apostrophe is the same character, and it does not end the value either. */
    @Test
    public void aJsonEscapedApostropheInsideAValueDoesNotEndIt() {
        assertEquals("https://h/?token=***&x=keep", scrub("https://h/?token=foo\\u0027" + OPAQUE + "&x=keep"));
    }

    /** A secret whose own characters are encoded is found in the decoded copy. */
    @Test
    public void aTokenWithEncodedCharactersIsStillFound() {
        assertEquals("123456789:***", scrub("123456789%3A%41" + SECRET_PART.substring(1)));
    }

    /** An encoded equals sign is part of the name, so {@code token%3Dpublic} is not a token parameter. */
    @Test
    public void anEncodedDelimiterIsDataNotStructure() {
        assertEquals("https://h/?token%3Dpublic=value", scrub("https://h/?token%3Dpublic=value"));
    }

    /** A JSON escape is Gson's spelling of a real ampersand: it ends the value and the neighbour stays. */
    @Test
    public void aJsonEscapedAmpersandEndsTheValue() {
        assertEquals("https://h/?token=***\\u0026x=keep", scrub("https://h/?token=" + OPAQUE + "\\u0026x=keep"));
    }

    /**
     * Strings inside strings spend the same budget as the structure around them. Here the
     * structure alone takes the walk to the limit, and the value is masked for depth, not by name.
     */
    @Test
    public void aStringInsideAStringSpendsTheSameDepthBudget() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 99; i++) {
            deep.append('[');
        }
        deep.append("\"{\\\"a\\\":\\\"").append(OPAQUE).append("\\\"}\"");
        for (int i = 0; i < 99; i++) {
            deep.append(']');
        }

        assertFalse(scrubBody(deep.toString()).contains(OPAQUE));
    }

    /**
     * A string at the limit is still parsed and the document inside it is masked by the budget,
     * whatever its keys are called. Not parsing it there would hand an unwalked document to rules
     * that cannot see a key.
     */
    @Test
    public void aDocumentInAStringAtTheLimitIsMaskedByTheBudget() {
        StringBuilder open  = new StringBuilder();
        StringBuilder close = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            open.append('[');
            close.append(']');
        }

        assertEquals(open + "\"\\\"***\\\"\"" + close,
                scrubBody(open + "\"{\\\"a\\\":\\\"" + OPAQUE + "\\\"}\"" + close));
    }

    /** Separate markers, each with its own span: building the result must not move the tail each time. */
    @Test
    public void manySeparateSpansDoNotCostQuadraticTime() {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 400_000; i++) {
            many.append("&token=x");
        }

        long started = System.nanoTime();
        String scrubbed = scrub(many.toString());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertEquals(400_000 * "&token=***".length(), scrubbed.length());
        // Linear takes tens of milliseconds here and in-place replacing about twenty seconds.
        assertTrue("took " + elapsedMs + "ms, which is the quadratic replace back", elapsedMs < 3_000);
    }

    /** The same string of markers, now cheap because each scan resumes where the last one stopped. */
    @Test
    public void thousandsOfMarkersDoNotCostQuadraticTime() {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 60_000; i++) {
            many.append("?token=");
        }
        many.append(OPAQUE);

        long started = System.nanoTime();
        String scrubbed = scrub(many.toString());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertFalse(scrubbed, scrubbed.contains(OPAQUE));
        // Linear takes tens of milliseconds here and quadratic tens of seconds, so the threshold
        // sits between two orders of magnitude rather than beside a measurement.
        assertTrue("took " + elapsedMs + "ms, which is the quadratic scan back", elapsedMs < 5_000);
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
