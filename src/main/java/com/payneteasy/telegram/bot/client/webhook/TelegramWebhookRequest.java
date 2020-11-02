package com.payneteasy.telegram.bot.client.webhook;

import lombok.Data;

@Data
public class TelegramWebhookRequest {

    private final String url;
    private final String botToken;

}
