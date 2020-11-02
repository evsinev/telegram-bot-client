package com.payneteasy.telegram.bot.client;

import com.payneteasy.telegram.bot.client.messages.TelegramMessageRequest;
import com.payneteasy.telegram.bot.client.model.TelegramMessage;
import com.payneteasy.telegram.bot.client.model.TelegramUser;
import com.payneteasy.telegram.bot.client.webhook.TelegramWebhookRequest;
import com.payneteasy.telegram.bot.client.webhook.TelegramWebhookResponse;

public interface ITelegramService {

    TelegramUser getMe();

    TelegramMessage sendMessage(TelegramMessageRequest aRequest);

    TelegramWebhookResponse setWebhook(TelegramWebhookRequest aRequest);

    TelegramWebhookResponse clearWebhook(String botToken);

    // setWebhook
    // {
    //  "ok": true,
    //  "result": true,
    //  "description": "Webhook was set"
    //}
    
}
