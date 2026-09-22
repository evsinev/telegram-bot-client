package com.payneteasy.telegram.bot.client.http;

/**
 * How the bot token reaches the server the client talks to.
 */
public enum TokenTransport {

    /**
     * Token is a path segment of the request URL: {@code <baseUrl><token>/<method>}.
     * This is what api.telegram.org expects, and it is the meaning of the constructors
     * that do not take a {@link TelegramHttpClientConfig}.
     */
    URL,

    /**
     * Token travels in a header, the URL carries no secret: {@code <baseUrl>/<method>}.
     * Meant for a proxy in front of Telegram that puts the token back into the URL itself.
     */
    HEADER
}
