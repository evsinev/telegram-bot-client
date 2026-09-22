package com.payneteasy.telegram.bot.client.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Builder;
import lombok.Getter;

/**
 * Everything {@link TelegramHttpClientImpl} needs, in one object.
 *
 * Unset {@code timeouts} and {@code gson} mean exactly what the single-argument
 * constructor of the client has always meant, so a caller that only wants a different
 * token transport does not have to restate them.
 */
@Getter
@Builder
public class TelegramHttpClientConfig {

    /**
     * Base address without the method name. In {@link TokenTransport#URL} mode the token is
     * appended to it directly (hence the trailing {@code /bot} of the Telegram address),
     * in {@link TokenTransport#HEADER} mode a {@code /} and the method name follow.
     */
    private final String baseUrl;

    private final String token;

    @Builder.Default
    private final TokenTransport tokenTransport = TokenTransport.URL;

    @Builder.Default
    private final String tokenHeaderName = "X-Telegram-Bot-Token";

    @Builder.Default
    private final HttpClientTimeouts timeouts = new HttpClientTimeouts(30_000, 30_000, 30_000);

    @Builder.Default
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

}
