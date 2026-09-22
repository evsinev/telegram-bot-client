package com.payneteasy.telegram.bot.client.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.payneteasy.telegram.bot.client.TelegramCommandException;
import com.payneteasy.telegram.bot.client.messages.TelegramStandardResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import static com.payneteasy.telegram.bot.client.http.ScrubbedCause.sanitize;
import static com.payneteasy.telegram.bot.client.http.TelegramLogScrubber.scrub;
import static com.payneteasy.telegram.bot.client.http.TelegramLogScrubber.scrubBody;
import static com.payneteasy.telegram.bot.client.http.TelegramLogScrubber.scrubFragment;
import static java.nio.charset.StandardCharsets.UTF_8;

public class TelegramHttpClientImpl implements ITelegramHttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(TelegramHttpClientImpl.class);

    private final String             baseUrl;
    private final String             token;
    private final TokenTransport     tokenTransport;
    private final String             tokenHeaderName;
    private final HttpClientTimeouts timeouts;
    private final Gson               gson;
    private final AtomicLong         commandId = new AtomicLong();

    public TelegramHttpClientImpl(TelegramHttpClientConfig aConfig) {
        this.baseUrl         = aConfig.getBaseUrl();
        this.token           = aConfig.getToken();
        this.tokenTransport  = aConfig.getTokenTransport();
        this.tokenHeaderName = aConfig.getTokenHeaderName();
        this.timeouts        = aConfig.getTimeouts();
        this.gson            = aConfig.getGson();
    }

    public TelegramHttpClientImpl(String baseUrl, String token, HttpClientTimeouts timeouts, Gson gson) {
        this(TelegramHttpClientConfig.builder()
                .baseUrl(baseUrl)
                .token(token)
                .timeouts(timeouts)
                .gson(gson)
                .build());
    }

    public TelegramHttpClientImpl(String token) {
        this("https://api.telegram.org/bot", token, new HttpClientTimeouts(30_000, 30_000, 30_000), new GsonBuilder().setPrettyPrinting().create());
    }

    /**
     * {@link TokenTransport#URL} keeps the address Telegram expects; {@link TokenTransport#HEADER}
     * leaves the token out of it entirely — there is nothing secret left in the request target.
     */
    String buildUrl(String aMethodName) {
        return tokenTransport == TokenTransport.HEADER
                ? baseUrl + "/" + aMethodName
                : baseUrl + token + "/" + aMethodName;
    }

    private void sendHeaders(SimpleHttpClient aClient) {
        aClient.sendHeader("Content-Type", "application/json");
        if (tokenTransport == TokenTransport.HEADER) {
            aClient.sendHeader(tokenHeaderName, token);
        }
    }

    @Override
    public <T> T get(String aMethodName, Class<T> aResponseClass) {
        String id = nextCommandId();

        SimpleHttpResponse response;
        String             json;
        try (SimpleHttpClient client = new SimpleHttpClient()) {
            client.connect(buildUrl(aMethodName), timeouts.getConnectionMs(), timeouts.getReadMs(), "GET");
            sendHeaders(client);
            LOG.debug("{} {}: request", id, aMethodName);
            response = client.fetchResponse();
            json     = new String(response.getBody(), UTF_8);
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} {}: response {}", id, aMethodName, scrubBody(json));
            }
        } catch (IOException | RuntimeException e) {
            throw cannotInvoke(aMethodName, id, e);
        }

        if (response.getStatusCode() != 200) {
            throw new IllegalStateException(scrubBody(json));
        }

        try {
            return gson.fromJson(json, aResponseClass);
        } catch (RuntimeException e) {
            throw cannotParse(aMethodName, id, e);
        }
    }

    /**
     * Wraps whatever the call threw, message and cause scrubbed.
     *
     * The catch is deliberately wider than {@code IOException}: the body of an answer reaches a
     * message by routes that are not IO at all — {@code gson.fromJson} names the offending value
     * in a {@code NumberFormatException}, and {@code setRequestProperty} names the header value it
     * rejected, which in HEADER mode is the token itself.
     */
    /**
     * A failure to turn the answer into a response class. The parser quotes the fragment it choked
     * on, which is a piece of the body, so neither its text nor its cause's text comes along: the
     * body is in the log already, walked or withheld, and the type names and stacks are what say
     * what went wrong.
     */
    private TelegramCommandException cannotParse(String aMethodName, String aId, Throwable aCause) {
        return new TelegramCommandException("Cannot parse the answer of " + aMethodName,
                ScrubbedCause.typeOnly(aCause), aId, -1);
    }

    private TelegramCommandException cannotInvoke(String aMethodName, String aId, Throwable aCause) {
        return new TelegramCommandException("Cannot invoke " + aMethodName + ": " + scrub(aCause.getMessage()), sanitize(aCause), aId, -1);
    }

    private String nextCommandId() {
        return String.valueOf(commandId.incrementAndGet());
    }

    private Integer parseRetryAfter(String responseJson) {
        try {
            TelegramStandardResponse error = gson.fromJson(responseJson, TelegramStandardResponse.class);
            if (error != null && error.getParameters() != null) {
                return error.getParameters().getRetryAfter();
            }
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Cannot parse error body for retry_after: {}", scrubBody(responseJson));
            }
        }
        return null;
    }

    @Override
    public <R, T> T post(String aMethodName, R aRequest, Class<T> aResponseClass) {
        String id = nextCommandId();
        return post(id, aMethodName, aRequest, aResponseClass);
    }

    private  <R, T> T post(String id, String aMethodName, R aRequest, Class<T> aResponseClass) {
        SimpleHttpResponse response;
        String             responseJson;
        try (SimpleHttpClient client = new SimpleHttpClient()) {
            client.connect(buildUrl(aMethodName), timeouts.getConnectionMs(), timeouts.getReadMs(), "POST");
            sendHeaders(client);
            String requestJson = gson.toJson(aRequest);
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} {}: request  {}", id, aMethodName, scrubBody(requestJson));
            }
            client.sendBody(requestJson.getBytes(UTF_8));
            response     = client.fetchResponse();
            responseJson = new String(response.getBody(), UTF_8);
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} {}: response {}", id, aMethodName, scrubBody(responseJson));
            }
        } catch (IOException | RuntimeException e) {
            throw cannotInvoke(aMethodName, id, e);
        }

        if (response.getStatusCode() != 200) {
            throw new TelegramCommandException(scrubBody(responseJson), id, response.getStatusCode(), parseRetryAfter(responseJson));
        }

        try {
            return gson.fromJson(responseJson, aResponseClass);
        } catch (RuntimeException e) {
            throw cannotParse(aMethodName, id, e);
        }
    }

    @Override
    public <R> void post(String aMethodName, R aRequest) {
        String id = nextCommandId();
        TelegramStandardResponse response = post(id, aMethodName, aRequest, TelegramStandardResponse.class);
        if(!response.isOk()) {
            // The description is a piece of the body, not free text of ours: if Telegram put a
            // structure there it gets walked, and only genuine prose goes through the flat rules.
            throw new TelegramCommandException(scrubFragment(response.getDescription()), id, response.getErrorCode());
        }
    }
}
