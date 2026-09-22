package com.payneteasy.telegram.bot.client.messages;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * : the optional secret_token must not change the body of callers that do not set it.
 */
public class TelegramWebhookRequestSerializationTest {

    private static final Gson GSON = new GsonBuilder().create();

    private static final String URL    = "https://webhook.example.com/telegram/webhook?bot_id=123456789";
    private static final String SECRET = "5d41402abc4b2a76b9719d911017c592a1b2c3d4e5f60718293a4b5c6d7e8f90";

    @Test
    public void withoutSecretTheBodyIsUrlOnly() {
        JsonObject body = serialize(new TelegramWebhookRequest(URL, null));

        assertEquals("[url]", keysOf(body));
        assertEquals(URL, body.get("url").getAsString());
    }

    @Test
    public void withSecretTheBodyCarriesBothFields() {
        JsonObject body = serialize(new TelegramWebhookRequest(URL, SECRET));

        assertEquals("[secret_token, url]", keysOf(body));
        assertEquals(URL, body.get("url").getAsString());
        assertEquals(SECRET, body.get("secret_token").getAsString());
    }

    /**
     * The path {@code clearWebhook} takes. Checked on a non-empty URL as well, or an
     * implementation that forwarded a constant {@code ""} would pass.
     */
    @Test
    public void singleArgumentConstructorSendsTheFormerBody() {
        TelegramWebhookRequest cleared = new TelegramWebhookRequest("");

        assertNull(cleared.getSecretToken());
        assertEquals("{\"url\":\"\"}", GSON.toJson(cleared));

        TelegramWebhookRequest set = new TelegramWebhookRequest(URL);

        assertNull(set.getSecretToken());
        assertEquals("[url]", keysOf(serialize(set)));
        assertEquals(URL, serialize(set).get("url").getAsString());
    }

    private static JsonObject serialize(TelegramWebhookRequest aRequest) {
        return new JsonParser().parse(GSON.toJson(aRequest)).getAsJsonObject();
    }

    private static String keysOf(JsonObject aBody) {
        return new TreeSet<String>(aBody.keySet()).toString();
    }
}
