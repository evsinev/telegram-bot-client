package com.payneteasy.telegram.bot.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.payneteasy.telegram.bot.client.messages.TelegramStandardResponse;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies parsing of Telegram error responses and that {@link TelegramCommandException} exposes the
 * HTTP error code and the retry_after hint (sent by Telegram on 429 Too Many Requests).
 */
public class TelegramErrorHandlingTest {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Test
    public void parsesRetryAfterFrom429Error() {
        String body = "{"
                + "\"ok\": false,"
                + "\"error_code\": 429,"
                + "\"description\": \"Too Many Requests: retry after 5\","
                + "\"parameters\": {\"retry_after\": 5}"
                + "}";

        TelegramStandardResponse response = GSON.fromJson(body, TelegramStandardResponse.class);

        assertFalse(response.isOk());
        assertEquals(Integer.valueOf(429), response.getErrorCode());
        assertNotNull(response.getParameters());
        assertEquals(Integer.valueOf(5), response.getParameters().getRetryAfter());
    }

    @Test
    public void okResponseHasNoParameters() {
        TelegramStandardResponse response =
                GSON.fromJson("{\"ok\": true}", TelegramStandardResponse.class);

        assertTrue(response.isOk());
        assertNull(response.getParameters());
    }

    @Test
    public void exceptionCarriesErrorCodeAndRetryAfter() {
        TelegramCommandException exception =
                new TelegramCommandException("Too Many Requests", "cmd-1", 429, 5);

        assertEquals("cmd-1", exception.getId());
        assertEquals(Integer.valueOf(429), exception.getErrorCode());
        assertEquals(Integer.valueOf(5), exception.getRetryAfter());
    }
}
