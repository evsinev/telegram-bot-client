package com.payneteasy.telegram.bot.client.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;

import static java.nio.charset.StandardCharsets.UTF_8;

public class TelegramHttpClientImpl implements ITelegramHttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(TelegramHttpClientImpl.class);

    private final String             baseUrl;
    private final String             token;
    private final HttpClientTimeouts timeouts;
    private final Gson               gson;

    public TelegramHttpClientImpl(String baseUrl, String token, HttpClientTimeouts timeouts, Gson gson) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.timeouts = timeouts;
        this.gson = gson;
    }

    public TelegramHttpClientImpl(String token) {
        this("https://api.telegram.org/bot", token, new HttpClientTimeouts(30_000, 30_000, 30_000), new GsonBuilder().setPrettyPrinting().create());
    }

    // 1279511245:AAFCou9TldP8ZPtDLN5nOZ1cWsRkpHEdOgI

    @Override
    public <T> T get(String aMethodName, Class<T> aResponseClass) {
        try {
            try (SimpleHttpClient client = new SimpleHttpClient()) {
                client.connect(baseUrl + token + "/" + aMethodName, timeouts.getConnectionMs(), timeouts.getReadMs(), "GET");
                client.sendHeader("Content-Type", "application/json");
                SimpleHttpResponse response = client.fetchResponse();
                if (response.getStatusCode() != 200) {
                    throw new IllegalStateException(new String(response.getBody(), UTF_8));
                }
                return gson.fromJson(new String(response.getBody(), UTF_8), aResponseClass);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot invoke " + aMethodName, e);
        }
    }

    @Override
    public <R, T> T post(String aMethodName, R aRequest, Class<T> aResponseClass) {
        try {
            try (SimpleHttpClient client = new SimpleHttpClient()) {
                client.connect(baseUrl + token + "/" + aMethodName, timeouts.getConnectionMs(), timeouts.getReadMs(), "GET");
                client.sendHeader("Content-Type", "application/json");
                client.sendBody(gson.toJson(aRequest).getBytes(UTF_8));
                SimpleHttpResponse response = client.fetchResponse();
                if (response.getStatusCode() != 200) {
                    throw new IllegalStateException(new String(response.getBody(), UTF_8));
                }
                return gson.fromJson(new String(response.getBody(), UTF_8), aResponseClass);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot invoke " + aMethodName, e);
        }
    }

}
