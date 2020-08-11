package com.payneteasy.telegram.bot.client;

import com.payneteasy.telegram.bot.client.messages.TelegramMessageRequest;
import com.payneteasy.telegram.bot.client.model.TelegramMessage;
import com.payneteasy.telegram.bot.client.model.TelegramUser;

public interface ITelegramService {

    TelegramUser getMe();

    TelegramMessage sendMessage(TelegramMessageRequest aRequest);

    // setWebhook
    // {
    //  "ok": true,
    //  "result": true,
    //  "description": "Webhook was set"
    //}
    
}
