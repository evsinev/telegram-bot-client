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
 * S1.1 and S1.3 against a live call: where the token ends up in the request, and that it ends up
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
    // S1.1 — transport
    // -----------------------------------------------------------------------

    @Test
    public void headerTransportPutsTheTokenInOneHeaderAndNotInTheUrl() {
        service(TokenTransport.HEADER, baseUrl + "/telegram").sendMessage(message());

        RecordedRequest request = single();
        assertEquals("/telegram/sendMessage", request.path);
        assertEquals("exactly one token header", 1, request.headers("X-Telegram-Bot-Token").size());
        assertEquals(TOKEN, request.headers("X-Telegram-Bot-Token").get(0));
        assertFalse("no token in the request target", request.path.contains("123456789"));
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
        assertEquals("/bot" + TOKEN + "/sendMessage", request.path);
        assertTrue("no token header in URL mode", request.headers("X-Telegram-Bot-Token").isEmpty());
    }

    // -----------------------------------------------------------------------
    // S1.3 — nothing secret in a log record or in an exception message
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
        responseBody   = "{\"ok\":false,\"description\":\"webhook is https://gate.pne.io/hook?bot_token=" + TOKEN + "\"}";

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
                new TelegramWebhookRequest("https://gate.pne.io/telegram/webhook?bot_id=123456789", SECRET));

        assertTrue("the real body still reaches the server", single().body.contains(SECRET));
        assertNoSecretsInLogs();
        assertTrue("the field is visible, its value is not", anyLogContains("\"secret_token\": \"***\""));
    }

    /** The description of a well-formed error answer — TelegramHttpClientImpl:105. */
    @Test
    public void descriptionOfAnErrorAnswerIsScrubbedInTheException() {
        responseBody = "{\"ok\":false,\"error_code\":401,\"description\":\"Unauthorized: " + TOKEN + "\"}";

        try {
            service(TokenTransport.HEADER, baseUrl + "/telegram").setWebhook(new TelegramWebhookRequest("https://gate.pne.io/hook"));
            fail("ok=false must not be swallowed");
        } catch (TelegramCommandException e) {
            assertNoSecrets("exception message", e.getMessage());
            assertTrue("bot_id stays readable", e.getMessage().contains("123456789:***"));
        }
    }

    // -----------------------------------------------------------------------

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

        private final String                            path;
        private final com.sun.net.httpserver.Headers    headers;
        private final String                            body;

        private RecordedRequest(HttpExchange aExchange) throws IOException {
            path    = aExchange.getRequestURI().getPath();
            headers = aExchange.getRequestHeaders();
            body    = new String(org.apache.commons.io.IOUtils.toByteArray(aExchange.getRequestBody()), UTF_8);
        }

        private List<String> headers(String aName) {
            List<String> values = headers.get(aName);
            return values == null ? new ArrayList<String>() : values;
        }
    }
}
