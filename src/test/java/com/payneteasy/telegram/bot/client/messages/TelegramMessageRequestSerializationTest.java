package com.payneteasy.telegram.bot.client.messages;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.payneteasy.telegram.bot.client.model.ParseMode;
import org.junit.Test;

import java.util.TreeSet;

import static org.junit.Assert.assertEquals;

/**
 * The sendMessage body has to stay inside the field allowlist of an intermediary that validates
 * it. A field added
 * here without a matching change on the proxy side turns every notification into a 400.
 */
public class TelegramMessageRequestSerializationTest {

    private static final Gson GSON = new GsonBuilder().create();

    @Test
    public void bodyCarriesExactlyTheThreeAllowedFields() {
        String json = GSON.toJson(TelegramMessageRequest.builder()
                .chatId(123456789L)
                .text("hello")
                .parseMode(ParseMode.HTML)
                .build());

        JsonObject object = new JsonParser().parse(json).getAsJsonObject();

        assertEquals("[chat_id, parse_mode, text]", new TreeSet<String>(object.keySet()).toString());
        assertEquals(123456789L, object.get("chat_id").getAsLong());
        assertEquals("hello", object.get("text").getAsString());
        assertEquals("HTML", object.get("parse_mode").getAsString());
    }
}
