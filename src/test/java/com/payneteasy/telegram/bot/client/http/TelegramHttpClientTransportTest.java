package com.payneteasy.telegram.bot.client.http;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.payneteasy.telegram.bot.client.TelegramCommandException;
import com.payneteasy.telegram.bot.client.impl.TelegramServiceImpl;
import com.payneteasy.telegram.bot.client.messages.TelegramMessageRequest;
import com.payneteasy.telegram.bot.client.messages.TelegramWebhookRequest;
import com.payneteasy.telegram.bot.client.model.ParseMode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 *  and  against a live call: where the token ends up in the request, and that it ends up
 * in neither a log record nor an exception message.
 *
 * T3 checks the scrubbing function; this one checks that it is applied on every path the client
 * has — the response body of a GET, the error body read for retry_after, the request and response
 * bodies of a POST, and the URL logged by {@link SimpleHttpClient}.
 */
public class TelegramHttpClientTransportTest {

    private static final String SECRET_PART = "AAHfake0Token1For2Tests3456789abcXYZ";
    private static final String TOKEN       = "123456789:" + SECRET_PART;
    private static final String SECRET      = "5d41402abc4b2a76b9719d911017c592a1b2c3d4e5f60718293a4b5c6d7e8f90";

    private HttpServer                 server;
    private String                     baseUrl;
    private List<RecordedRequest>      requests;
    private ListAppender<ILoggingEvent> logs;
    private final List<Logger>         patched = new ArrayList<Logger>();

    /** Answer of the next call: status plus body. Set per test. */
    private int    responseStatus = 200;
    private String responseBody   = "{\"ok\":true}";

    @Before
    public void setUp() throws IOException {
        requests = new CopyOnWriteArrayList<RecordedRequest>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new com.sun.net.httpserver.HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                requests.add(new RecordedRequest(exchange));
                byte[] body = responseBody.getBytes(UTF_8);
                exchange.sendResponseHeaders(responseStatus, body.length);
                OutputStream out = exchange.getResponseBody();
                out.write(body);
                out.close();
                exchange.close();
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        logs = new ListAppender<ILoggingEvent>();
        logs.start();
        captureDebug(TelegramHttpClientImpl.class);
        captureDebug(SimpleHttpClient.class);
    }

    @After
    public void tearDown() {
        for (Logger logger : patched) {
            logger.detachAppender(logs);
        }
        server.stop(0);
    }

    // -----------------------------------------------------------------------
    //  — transport
    // -----------------------------------------------------------------------

    @Test
    public void headerTransportPutsTheTokenInOneHeaderAndNotInTheUrl() {
        service(TokenTransport.HEADER, baseUrl + "/telegram").sendMessage(message());

        RecordedRequest request = single();
        assertEquals("the whole request target, query included", "/telegram/sendMessage", request.target);
        assertEquals("exactly one token header", 1, request.headers("X-Telegram-Bot-Token").size());
        assertEquals(TOKEN, request.headers("X-Telegram-Bot-Token").get(0));
        assertFalse("no token in the request target", request.target.contains("123456789"));
    }

    @Test
    public void headerTransportHonoursACustomHeaderName() {
        new TelegramServiceImpl(new TelegramHttpClientImpl(TelegramHttpClientConfig.builder()
                .baseUrl(baseUrl + "/telegram")
                .token(TOKEN)
                .tokenTransport(TokenTransport.HEADER)
                .tokenHeaderName("X-Custom-Token")
                .build())).sendMessage(message());

        RecordedRequest request = single();
        assertEquals(TOKEN, request.headers("X-Custom-Token").get(0));
        assertTrue(request.headers("X-Telegram-Bot-Token").isEmpty());
    }

    @Test
    public void urlTransportSendsNoTokenHeader() {
        service(TokenTransport.URL, baseUrl + "/bot").sendMessage(message());

        RecordedRequest request = single();
        assertEquals("/bot" + TOKEN + "/sendMessage", request.target);
        assertTrue("no token header in URL mode", request.headers("X-Telegram-Bot-Token").isEmpty());
    }

    // -----------------------------------------------------------------------
    //  — nothing secret in a log record or in an exception message
    // -----------------------------------------------------------------------

    /** SimpleHttpClient:21 — the assembled URL, which in URL mode carries the token. */
    @Test
    public void urlOfTheCallIsScrubbedInTheLog() {
        service(TokenTransport.URL, baseUrl + "/bot").sendMessage(message());

        assertNoSecretsInLogs();
        assertTrue("bot_id is what makes the record useful", anyLogContains("123456789:***"));
    }

    /** TelegramHttpClientImpl:46 and :48 — the body of a GET answer and the exception built from it. */
    @Test
    public void failedGetLeaksNothingToTheLogOrToTheException() {
        responseStatus = 500;
        responseBody   = "{\"ok\":false,\"description\":\"webhook is https://webhook.example.com/hook?bot_token=" + TOKEN + "\"}";

        try {
            service(TokenTransport.URL, baseUrl + "/bot").getMe();
            fail("500 must not be swallowed");
        } catch (IllegalStateException e) {
            assertNoSecrets("exception message", e.getMessage());
            assertNotNull(e.getMessage());
        }

        assertNoSecretsInLogs();
    }

    /** TelegramHttpClientImpl:68, :89 and :91 — an error body that does not parse. */
    @Test
    public void failedPostLeaksNothingToTheLogOrToTheException() {
        responseStatus = 429;
        responseBody   = "Too Many Requests for bot " + TOKEN + ", retry later";

        try {
            service(TokenTransport.URL, baseUrl + "/bot").sendMessage(message());
            fail("429 must not be swallowed");
        } catch (TelegramCommandException e) {
            assertNoSecrets("exception message", e.getMessage());
            assertEquals(Integer.valueOf(429), e.getErrorCode());
            assertNull("an unparsable body yields no retry_after", e.getRetryAfter());
        }

        assertNoSecretsInLogs();
    }

    /** TelegramHttpClientImpl:85 — the request body, which for setWebhook holds both secrets. */
    @Test
    public void setWebhookRequestBodyIsScrubbedInTheLog() {
        service(TokenTransport.HEADER, baseUrl + "/telegram").setWebhook(
                new TelegramWebhookRequest("https://webhook.example.com/telegram/webhook?bot_id=123456789", SECRET));

        assertTrue("the real body still reaches the server", single().body.contains(SECRET));
        assertNoSecretsInLogs();
        assertTrue("the field is visible, its value is not", anyLogContains("\"secret_token\":\"***\""));
    }

    /** The description of a well-formed error answer — TelegramHttpClientImpl:105. */
    @Test
    public void descriptionOfAnErrorAnswerIsScrubbedInTheException() {
        responseBody = "{\"ok\":false,\"error_code\":401,\"description\":\"Unauthorized: " + TOKEN + "\"}";

        try {
            service(TokenTransport.HEADER, baseUrl + "/telegram").setWebhook(new TelegramWebhookRequest("https://webhook.example.com/hook"));
            fail("ok=false must not be swallowed");
        } catch (TelegramCommandException e) {
            assertNoSecrets("exception message", e.getMessage());
            assertTrue("bot_id stays readable", e.getMessage().contains("123456789:***"));
        }
    }

    /** Not only that 429 is raised, but that the hint the client parsed comes out (). */
    @Test
    public void retryAfterOfAParsableErrorReachesTheCaller() {
        responseStatus = 429;
        responseBody   = "{\"ok\":false,\"error_code\":429,\"description\":\"Too Many Requests\",\"parameters\":{\"retry_after\":5}}";

        try {
            service(TokenTransport.URL, baseUrl + "/bot").sendMessage(message());
            fail("429 must not be swallowed");
        } catch (TelegramCommandException e) {
            assertEquals(Integer.valueOf(429), e.getErrorCode());
            assertEquals("the retry hint has to survive the trip", Integer.valueOf(5), e.getRetryAfter());
        }
    }

    /**
     * A 200 whose body does not fit the response class: the failure comes out of Gson, not out of
     * IO, and Gson names the value it choked on — which here is the token.
     */
    @Test
    public void malformedAnswerOnTwoHundredLeaksNothing() {
        responseBody = "{\"ok\":false,\"error_code\":\"" + TOKEN + "\"}";

        try {
            service(TokenTransport.HEADER, baseUrl + "/telegram").setWebhook(new TelegramWebhookRequest("https://webhook.example.com/hook"));
            fail("a body that does not fit the response class must not pass");
        } catch (RuntimeException e) {
            assertNoSecretsInChain(e);
        }
    }

    /**
     * The description of a refusal is a piece of the body, not prose of ours. Telegram putting a
     * structure there must be walked; it reaches the caller as the message of the exception.
     */
    @Test
    public void aDescriptionThatIsAStructureIsWalkedNotFlattened() {
        responseBody = "{\"ok\":false,\"error_code\":400,\"description\":\"{\\\"secret_token\\\":\\\"" + SECRET + "\\\"}\"}";

        try {
            service(TokenTransport.HEADER, baseUrl + "/telegram").setWebhook(new TelegramWebhookRequest("https://h/hook"));
            fail("ok=false must not be swallowed");
        } catch (TelegramCommandException e) {
            assertNoSecretsInChain(e);
        }
    }

    /**
     * A description that does not parse but names the secret field: before, a comment in front of a
     * truncated document or a percent-encoded byte order mark sent it out as prose, into both the
     * exception and the DEBUG record of the response.
     */
    @Test
    public void aDescriptionThatNamesTheSecretButDoesNotParseIsWithheld() {
        for (String description : new String[] {
                "/*prefix*/{\\\"secret_token\\\":\\\"" + SECRET + "\\\"",
                "%EF%BB%BF%7B%22secret_token%22:%22" + SECRET + "%22%7D",
        }) {
            responseBody = "{\"ok\":false,\"error_code\":400,\"description\":\"" + description + "\"}";

            try {
                service(TokenTransport.HEADER, baseUrl + "/telegram").setWebhook(new TelegramWebhookRequest("https://h/hook"));
                fail("ok=false must not be swallowed");
            } catch (TelegramCommandException e) {
                assertNoSecretsInChain(e);
                assertTrue(e.getMessage(), e.getMessage().contains("withheld"));
            }
        }
    }

    /**
     * The parser quotes the fragment it choked on, and that fragment is a piece of the body. Here
     * it is a webhook secret rather than a token, so nothing about its shape would save it — only
     * dropping the parser's text does.
     */
    @Test
    public void aParseFailureDoesNotCarryTheFragmentItChokedOn() {
        responseBody = "{\"ok\":false,\"error_code\":\"{\\\"secret_token\\\":\\\"" + SECRET + "\\\"}\"}";

        try {
            service(TokenTransport.HEADER, baseUrl + "/telegram").setWebhook(new TelegramWebhookRequest("https://h/hook"));
            fail("a body that does not fit the response class must not pass");
        } catch (RuntimeException e) {
            assertNoSecretsInChain(e);
            assertTrue("the diagnosis has to survive", renderChain(e).contains("Cannot parse the answer"));
        }
    }

    /**
     * A base address without a scheme: MalformedURLException names the whole URL, and in URL mode
     * that URL holds the token. The cause travels out of the client, so it has to be scrubbed too.
     */
    @Test
    public void causeChainOfAFailedCallLeaksNothing() {
        try {
            new TelegramServiceImpl(new TelegramHttpClientImpl(TelegramHttpClientConfig.builder()
                    .baseUrl("bad/bot")
                    .token(TOKEN)
                    .build())).getMe();
            fail("a malformed base address must not pass");
        } catch (RuntimeException e) {
            assertNoSecretsInChain(e);
            assertTrue("the diagnosis must survive", renderChain(e).contains("MalformedURLException"));
        }
    }

    /** A token that cannot go into a header at all — the failure names the value it rejected. */
    @Test
    public void rejectedHeaderValueLeaksNothing() {
        try {
            new TelegramServiceImpl(new TelegramHttpClientImpl(TelegramHttpClientConfig.builder()
                    .baseUrl(baseUrl + "/telegram")
                    .token(TOKEN + "\nX-Injected: 1")
                    .tokenTransport(TokenTransport.HEADER)
                    .build())).sendMessage(message());
            fail("a header value with a line break must not pass");
        } catch (RuntimeException e) {
            assertNoSecretsInChain(e);
        }
    }

    // -----------------------------------------------------------------------

    /** Renders the exception the way a scheduler would, so the whole chain is on trial. */
    private static String renderChain(Throwable aThrowable) {
        java.io.StringWriter writer = new java.io.StringWriter();
        aThrowable.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString();
    }

    private void assertNoSecretsInChain(Throwable aThrowable) {
        assertNoSecrets("rendered exception chain", renderChain(aThrowable));
        assertNoSecretsInLogs();
    }

    private TelegramServiceImpl service(TokenTransport aTransport, String aBaseUrl) {
        return new TelegramServiceImpl(new TelegramHttpClientImpl(TelegramHttpClientConfig.builder()
                .baseUrl(aBaseUrl)
                .token(TOKEN)
                .tokenTransport(aTransport)
                .build()));
    }

    private static TelegramMessageRequest message() {
        return TelegramMessageRequest.builder().chatId(42L).text("hello").parseMode(ParseMode.HTML).build();
    }

    private RecordedRequest single() {
        assertEquals("expected exactly one call", 1, requests.size());
        return requests.get(0);
    }

    private void captureDebug(Class<?> aClass) {
        Logger logger = (Logger) LoggerFactory.getLogger(aClass);
        logger.setLevel(Level.DEBUG);
        logger.addAppender(logs);
        patched.add(logger);
    }

    private boolean anyLogContains(String aText) {
        for (ILoggingEvent event : logs.list) {
            if (event.getFormattedMessage().contains(aText)) {
                return true;
            }
        }
        return false;
    }

    private void assertNoSecretsInLogs() {
        assertFalse("nothing was logged at all — the test would pass vacuously", logs.list.isEmpty());
        for (ILoggingEvent event : logs.list) {
            assertNoSecrets("log record", event.getFormattedMessage());
        }
    }

    private static void assertNoSecrets(String aWhere, String aText) {
        if (aText == null) {
            return;
        }
        assertFalse(aWhere + " contains the bot token: " + aText, aText.contains(TOKEN));
        assertFalse(aWhere + " contains the token secret: " + aText, aText.contains(SECRET_PART));
        assertFalse(aWhere + " contains the webhook secret: " + aText, aText.contains(SECRET));
    }

    private static final class RecordedRequest {

        private final String                            target;
        private final com.sun.net.httpserver.Headers    headers;
        private final String                            body;

        private RecordedRequest(HttpExchange aExchange) throws IOException {
            target  = aExchange.getRequestURI().toString();
            headers = aExchange.getRequestHeaders();
            body    = new String(org.apache.commons.io.IOUtils.toByteArray(aExchange.getRequestBody()), UTF_8);
        }

        private List<String> headers(String aName) {
            List<String> values = headers.get(aName);
            return values == null ? new ArrayList<String>() : values;
        }
    }
}
