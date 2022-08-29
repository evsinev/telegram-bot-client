package com.payneteasy.telegram.bot.client.impl;

import com.payneteasy.telegram.bot.client.ITelegramService;
import com.payneteasy.telegram.bot.client.http.ITelegramHttpClient;
import com.payneteasy.telegram.bot.client.messages.*;
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

    @Override
    public void setWebhook(TelegramWebhookRequest aRequest) {
        http.post("setWebhook", aRequest);
    }

    @Override
    public void clearWebhook() {
        http.post("setWebhook", new TelegramWebhookRequest(""));
    }

    @Override
    public void setMyCommands(TelegramSetMyCommandsRequest aMyCommands) {
        http.post("setMyCommands", aMyCommands);
    }

    @Override
    public void sendChatAction(ChatActionRequest aChatActionRequest) {
        http.post("sendChatAction", aChatActionRequest);
    }

    @Override
    public TelegramGetUpdatesResponse getUpdates(TelegramGetUpdatesRequest aRequest) {
        return http.post("getUpdates", aRequest, TelegramGetUpdatesResponse.class);
    }

    @Override
    public TelegramMessage editMessageText(EditMessageTextRequest aRequest) {
        return http.post("editMessageText", aRequest, TelegramMessage.class);
    }
}
