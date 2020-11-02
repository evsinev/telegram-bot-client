package com.payneteasy.telegram.bot.client.impl;

import com.payneteasy.telegram.bot.client.ITelegramService;
import com.payneteasy.telegram.bot.client.http.ITelegramHttpClient;
import com.payneteasy.telegram.bot.client.messages.TelegramMessageRequest;
import com.payneteasy.telegram.bot.client.model.TelegramMessage;
import com.payneteasy.telegram.bot.client.model.TelegramUser;
import com.payneteasy.telegram.bot.client.webhook.TelegramWebhookRequest;
import com.payneteasy.telegram.bot.client.webhook.TelegramWebhookResponse;

public class TelegramServiceImpl implements ITelegramService {

    private final ITelegramHttpClient http;

    public TelegramServiceImpl(ITelegramHttpClient aHttp) {
        http = aHttp;
    }

    @Override
    public TelegramUser getMe() {
        return http.get("getMe", TelegramUser.class);
    }

    @Override
    public TelegramMessage sendMessage(TelegramMessageRequest aRequest) {
        return http.post("sendMessage", aRequest, TelegramMessage.class);
    }


    @Override
    public TelegramWebhookResponse setWebhook(TelegramWebhookRequest aRequest) {
        return http.post("setWebhook", aRequest, TelegramWebhookResponse.class);
    }

    @Override
    public TelegramWebhookResponse clearWebhook(String botToken) {
        TelegramWebhookRequest request = new TelegramWebhookRequest("", botToken);
        return http.post("setWebhook", request, TelegramWebhookResponse.class);
    }
}
