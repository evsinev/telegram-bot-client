package com.payneteasy.telegram.bot.client.messages.invoice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that the payment request DTOs serialize into the exact JSON shape the Bot API expects:
 * snake_case keys, correct scalar types, ordered price arrays and omitted optional nulls.
 */
public class InvoiceSerializationTest {

    /** Same configuration as {@code TelegramHttpClientImpl}: no naming policy, mapping via @SerializedName. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static JsonObject toJson(Object value) {
        return JsonParser.parseString(GSON.toJson(value)).getAsJsonObject();
    }

    @Test
    public void sendInvoice_usesSnakeCaseKeysAndLongChatId() {
        long chatId = 123_456_789_012L; // larger than Integer.MAX_VALUE — must survive as a Long

        TelegramInvoiceRequest request = TelegramInvoiceRequest.builder()
                .chatId(chatId)
                .title("Premium subscription")
                .description("One month of premium access")
                .payload("order-42")
                .paymentProviderToken("PROVIDER_TOKEN")
                .currency("USD")
                .labeledPrices(Collections.singletonList(
                        LabeledPrice.builder().label("Subscription").amount(1500).build()))
                .build();

        JsonObject json = toJson(request);

        assertEquals(chatId, json.get("chat_id").getAsLong());
        assertEquals("PROVIDER_TOKEN", json.get("provider_token").getAsString());
        assertEquals("USD", json.get("currency").getAsString());
        assertTrue("prices must be a JSON array", json.get("prices").isJsonArray());

        // no serializeNulls in production Gson → optional null fields must not appear
        assertFalse("null optional fields must be omitted", json.has("photo_url"));
        assertFalse(json.has("max_tip_amount"));

        JsonObject price = json.getAsJsonArray("prices").get(0).getAsJsonObject();
        assertEquals("Subscription", price.get("label").getAsString());
        assertEquals(1500, price.get("amount").getAsInt());
    }

    @Test
    public void invoicePrices_preserveInsertionOrder() {
        TelegramInvoiceRequest request = TelegramInvoiceRequest.builder()
                .chatId(1L)
                .title("t").description("d").payload("p")
                .paymentProviderToken("tok").currency("USD")
                .labeledPrices(Arrays.asList(
                        LabeledPrice.builder().label("A").amount(100).build(),
                        LabeledPrice.builder().label("B").amount(200).build(),
                        LabeledPrice.builder().label("C").amount(300).build()))
                .build();

        JsonArray prices = toJson(request).getAsJsonArray("prices");

        assertEquals(3, prices.size());
        assertEquals("A", prices.get(0).getAsJsonObject().get("label").getAsString());
        assertEquals("B", prices.get(1).getAsJsonObject().get("label").getAsString());
        assertEquals("C", prices.get(2).getAsJsonObject().get("label").getAsString());
    }

    @Test
    public void answerPreCheckoutQuery_serializesIdAsString() {
        TelegramAnswerPreCheckoutQueryRequest request = TelegramAnswerPreCheckoutQueryRequest.builder()
                .preCheckoutQueryId("12345678901234567890")
                .ok(true)
                .build();

        JsonObject json = toJson(request);

        assertTrue("pre_checkout_query_id must be a JSON string",
                json.get("pre_checkout_query_id").getAsJsonPrimitive().isString());
        assertEquals("12345678901234567890", json.get("pre_checkout_query_id").getAsString());
        assertTrue(json.get("ok").getAsBoolean());
        assertFalse(json.has("error_message"));
    }

    @Test
    public void answerShippingQuery_serializesOptionsAsArray() {
        TelegramAnswerShippingQueryRequest request = TelegramAnswerShippingQueryRequest.builder()
                .shippingQueryId("query-1")
                .ok(true)
                .shippingOptions(Collections.singletonList(
                        ShippingOption.builder()
                                .id("express")
                                .title("Express")
                                .prices(Collections.singletonList(
                                        LabeledPrice.builder().label("Delivery").amount(500).build()))
                                .build()))
                .build();

        JsonObject json = toJson(request);

        assertTrue(json.get("shipping_query_id").getAsJsonPrimitive().isString());

        JsonArray options = json.getAsJsonArray("shipping_options");
        assertEquals(1, options.size());
        JsonObject option = options.get(0).getAsJsonObject();
        assertEquals("express", option.get("id").getAsString());
        assertEquals("Express", option.get("title").getAsString());
        assertEquals("Delivery",
                option.getAsJsonArray("prices").get(0).getAsJsonObject().get("label").getAsString());
    }
}
