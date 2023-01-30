package com.payneteasy.telegram.bot.client.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.payneteasy.telegram.bot.client.TelegramCommandException;
import com.payneteasy.telegram.bot.client.messages.IChatId;
import com.payneteasy.telegram.bot.client.messages.TelegramStandardResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static java.nio.charset.StandardCharsets.UTF_8;

public class TelegramHttpClientImpl implements ITelegramHttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(TelegramHttpClientImpl.class);

    private final String             baseUrl;
    private final String             token;
    private final HttpClientTimeouts timeouts;
    private final Gson               gson;
    private final AtomicLong         commandId = new AtomicLong();

    public TelegramHttpClientImpl(String baseUrl, String token, HttpClientTimeouts timeouts, Gson gson) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.timeouts = timeouts;
        this.gson = gson;
    }

    public TelegramHttpClientImpl(String token) {
        this("https://api.telegram.org/bot", token, new HttpClientTimeouts(30_000, 30_000, 30_000), new GsonBuilder().setPrettyPrinting().create());
    }

    @Override
    public <T> T get(String aMethodName, Class<T> aResponseClass) {
        String id = nextCommandId();
        try {
            try (SimpleHttpClient client = new SimpleHttpClient()) {
                client.connect(baseUrl + token + "/" + aMethodName, timeouts.getConnectionMs(), timeouts.getReadMs(), "GET");
                client.sendHeader("Content-Type", "application/json");
                LOG.debug("{} {}: request", id, aMethodName);
                SimpleHttpResponse response = client.fetchResponse();
                String             json        = new String(response.getBody(), UTF_8);
                LOG.debug("{} {}: response {}", id, aMethodName, json);
                if (response.getStatusCode() != 200) {
                    logUnsuccessfulResponseCode(id, response.getStatusCode(), null);
                    throw new IllegalStateException(json);
                }
                return gson.fromJson(json, aResponseClass);
            }
        } catch (IOException e) {
            throw new TelegramCommandException("Cannot invoke " + aMethodName, e, id, -1);
        }
    }

    private String nextCommandId() {
        return String.valueOf(commandId.incrementAndGet());
    }

    @Override
    public <R, T> T post(String aMethodName, R aRequest, Class<T> aResponseClass) {
        String id = nextCommandId();
        return post(id, aMethodName, aRequest, aResponseClass);
    }

    private  <R, T> T post(String id, String aMethodName, R aRequest, Class<T> aResponseClass) {
        try {
            try (SimpleHttpClient client = new SimpleHttpClient()) {
                client.connect(baseUrl + token + "/" + aMethodName, timeouts.getConnectionMs(), timeouts.getReadMs(), "POST");
                client.sendHeader("Content-Type", "application/json");
                String requestJson = gson.toJson(aRequest);
                LOG.debug("{} {}: request  {}", id, aMethodName, requestJson);
                client.sendBody(requestJson.getBytes(UTF_8));
                SimpleHttpResponse response = client.fetchResponse();
                String             responseJson     = new String(response.getBody(), UTF_8);
                LOG.debug("{} {}: response {}", id, aMethodName, responseJson);
                if (response.getStatusCode() != 200) {
                    logUnsuccessfulResponseCode(id, response.getStatusCode(), aRequest);
                    throw new TelegramCommandException(responseJson, id, -2);
                }
                return gson.fromJson(responseJson, aResponseClass);
            }
        } catch (IOException e) {
            throw new TelegramCommandException("Cannot invoke " + aMethodName, e, id, -1);
        }
    }

    @Override
    public <R> void post(String aMethodName, R aRequest) {
        String id = nextCommandId();
        TelegramStandardResponse response = post(id, aMethodName, aRequest, TelegramStandardResponse.class);
        if(!response.isOk()) {
            throw new TelegramCommandException(response.getDescription(), id, response.getErrorCode());
        }
    }

    private <R> void logUnsuccessfulResponseCode(String id, int statusCode, R aRequest) {
        Integer chatId = null;
        if (Objects.nonNull(aRequest) && aRequest instanceof IChatId) {
            chatId = ((IChatId) aRequest).getChatId();
        }
        LOG.warn("Unsuccessful response status code: {}, id: {}, bot token: {}, chat id: {}", statusCode, id, token, chatId);
    }
}
