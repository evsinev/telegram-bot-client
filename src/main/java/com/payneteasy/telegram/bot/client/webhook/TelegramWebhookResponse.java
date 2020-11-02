package com.payneteasy.telegram.bot.client.webhook;

import lombok.Data;

@Data
public class TelegramWebhookResponse {
    // {"ok":true,"result":true,"description":"Webhook was set"}
    private final boolean ok;

    private final boolean result;

    private final String  description;

}
