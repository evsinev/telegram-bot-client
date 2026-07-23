package com.payneteasy.telegram.bot.client.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.payneteasy.telegram.bot.client.model.invoice.OrderInfo;
import com.payneteasy.telegram.bot.client.model.invoice.PreCheckoutQuery;
import com.payneteasy.telegram.bot.client.model.invoice.ShippingQuery;
import com.payneteasy.telegram.bot.client.model.invoice.SuccessfulPayment;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Verifies that incoming payment updates deserialize into the model classes, with query ids kept as
 * strings and nested objects (order_info, shipping_address) fully readable.
 */
public class UpdateDeserializationTest {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Test
    public void deserializesPreCheckoutQuery() {
        String json = "{"
                + "\"update_id\": 100,"
                + "\"pre_checkout_query\": {"
                + "  \"id\": \"abc-123\","
                + "  \"from\": {\"id\": 42, \"first_name\": \"Alice\", \"is_bot\": false},"
                + "  \"currency\": \"USD\","
                + "  \"total_amount\": 1500,"
                + "  \"invoice_payload\": \"order-42\""
                + "}}";

        Update update = GSON.fromJson(json, Update.class);
        PreCheckoutQuery query = update.getPreCheckoutQuery();

        assertNotNull(query);
        assertEquals("abc-123", query.getId()); // id is a String, not a number
        assertEquals("USD", query.getCurrency());
        assertEquals(Integer.valueOf(1500), query.getTotalAmount());
        assertEquals("order-42", query.getInvoicePayload());
        assertEquals("Alice", query.getFrom().getFirstName());
        assertNull("shipping_query absent → null", update.getShippingQuery());
    }

    @Test
    public void deserializesShippingQuery() {
        String json = "{"
                + "\"update_id\": 101,"
                + "\"shipping_query\": {"
                + "  \"id\": \"ship-9\","
                + "  \"from\": {\"id\": 7, \"first_name\": \"Bob\"},"
                + "  \"invoice_payload\": \"cart-9\","
                + "  \"shipping_address\": {"
                + "    \"country_code\": \"US\", \"state\": \"CA\", \"city\": \"San Francisco\","
                + "    \"street_line1\": \"1 Main St\", \"street_line2\": \"\", \"post_code\": \"94016\""
                + "  }}}";

        ShippingQuery query = GSON.fromJson(json, Update.class).getShippingQuery();

        assertNotNull(query);
        assertEquals("ship-9", query.getId());
        assertEquals("cart-9", query.getInvoicePayload());
        assertNotNull(query.getShippingAddress());
        assertEquals("US", query.getShippingAddress().getCountryCode());
        assertEquals("94016", query.getShippingAddress().getPostCode());
    }

    @Test
    public void deserializesSuccessfulPaymentWithOrderInfo() {
        String json = "{"
                + "\"message_id\": 5,"
                + "\"successful_payment\": {"
                + "  \"currency\": \"USD\","
                + "  \"total_amount\": 2000,"
                + "  \"invoice_payload\": \"order-42\","
                + "  \"telegram_payment_charge_id\": \"tg-charge\","
                + "  \"provider_payment_charge_id\": \"pr-charge\","
                + "  \"order_info\": {"
                + "    \"name\": \"Alice\", \"phone_number\": \"+123\", \"email\": \"a@example.com\""
                + "  }}}";

        Message message = GSON.fromJson(json, Message.class);
        SuccessfulPayment payment = message.getSuccessfulPayment();

        assertNotNull(payment);
        assertEquals("USD", payment.getCurrency());
        assertEquals(Integer.valueOf(2000), payment.getTotalAmount());
        assertEquals("tg-charge", payment.getTelegramPaymentChargeId());
        assertEquals("pr-charge", payment.getProviderPaymentChargeId());

        // Regression guard: OrderInfo must carry @Data so its fields are readable.
        OrderInfo orderInfo = payment.getOrderInfo();
        assertNotNull("order_info must deserialize", orderInfo);
        assertEquals("Alice", orderInfo.getUserName());
        assertEquals("+123", orderInfo.getUserPhoneNumber());
        assertEquals("a@example.com", orderInfo.getUserEmail());
    }

    @Test
    public void plainMessageHasNoSuccessfulPayment() {
        Message message = GSON.fromJson("{\"message_id\": 1, \"text\": \"hi\"}", Message.class);

        assertEquals("hi", message.getText());
        assertNull(message.getSuccessfulPayment());
    }
}
