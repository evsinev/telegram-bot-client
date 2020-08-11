package com.payneteasy.telegram.bot.client.impl;

import com.payneteasy.telegram.bot.client.ITelegramService;
import com.payneteasy.telegram.bot.client.http.ITelegramHttpClient;
import com.payneteasy.telegram.bot.client.messages.TelegramMessageRequest;
import com.payneteasy.telegram.bot.client.model.TelegramMessage;
import com.payneteasy.telegram.bot.client.model.TelegramUser;

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
}
