package com.payneteasy.telegram.bot.client.http;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * : the address the client builds, per token transport.
 */
public class TelegramHttpClientUrlTest {

    private static final String TOKEN = "123456789:AAHfake0Token1For2Tests3456789abcXYZ";

    @Test
    public void urlTransportKeepsTokenInTheAddress() {
        TelegramHttpClientImpl client = new TelegramHttpClientImpl(TelegramHttpClientConfig.builder()
                .baseUrl("https://api.telegram.org/bot")
                .token(TOKEN)
                .build());

        assertEquals("https://api.telegram.org/bot" + TOKEN + "/sendMessage", client.buildUrl("sendMessage"));
    }

    @Test
    public void urlTransportIsTheDefaultOfTheConfig() {
        TelegramHttpClientConfig config = TelegramHttpClientConfig.builder()
                .baseUrl("https://api.telegram.org/bot")
                .token(TOKEN)
                .build();

        assertEquals(TokenTransport.URL, config.getTokenTransport());
        assertEquals("X-Telegram-Bot-Token", config.getTokenHeaderName());
    }

    @Test
    public void headerTransportLeavesNoTokenInTheAddress() {
        TelegramHttpClientImpl client = new TelegramHttpClientImpl(TelegramHttpClientConfig.builder()
                .baseUrl("http://proxy.example.com/telegram")
                .token(TOKEN)
                .tokenTransport(TokenTransport.HEADER)
                .build());

        String url = client.buildUrl("sendMessage");

        assertEquals("http://proxy.example.com/telegram/sendMessage", url);
        assertFalse("token must not appear in the request target", url.contains(TOKEN));
        assertFalse("not even the secret part of it", url.contains("AAHfake0Token1For2Tests3456789abcXYZ"));
    }

    @Test
    public void legacyConstructorsMeanUrlTransport() {
        assertEquals("https://api.telegram.org/bot" + TOKEN + "/getMe",
                new TelegramHttpClientImpl(TOKEN).buildUrl("getMe"));

        assertEquals("https://example.org/bot" + TOKEN + "/getMe",
                new TelegramHttpClientImpl("https://example.org/bot", TOKEN, new HttpClientTimeouts(1, 2, 3), null).buildUrl("getMe"));
    }
}
