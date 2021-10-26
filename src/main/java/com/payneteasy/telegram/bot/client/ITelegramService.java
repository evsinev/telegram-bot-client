package com.payneteasy.telegram.bot.client;

import com.payneteasy.telegram.bot.client.messages.ChatActionRequest;
import com.payneteasy.telegram.bot.client.messages.TelegramMessageRequest;
import com.payneteasy.telegram.bot.client.messages.TelegramSetMyCommandsRequest;
import com.payneteasy.telegram.bot.client.messages.invoice.TelegramAnswerPreCheckoutQueryRequest;
import com.payneteasy.telegram.bot.client.messages.invoice.TelegramInvoiceRequest;
import com.payneteasy.telegram.bot.client.model.TelegramMessage;
import com.payneteasy.telegram.bot.client.model.TelegramUser;
import com.payneteasy.telegram.bot.client.messages.TelegramWebhookRequest;

public interface ITelegramService {

    TelegramUser getMe();

    TelegramMessage sendMessage(TelegramMessageRequest aRequest);

    void setWebhook(TelegramWebhookRequest aRequest);

    void clearWebhook();

    void setMyCommands(TelegramSetMyCommandsRequest aMyCommands);

    void sendChatAction(ChatActionRequest aChatActionRequest);


    void sendInvoice(TelegramInvoiceRequest telegramInvoiceRequest);

    void answerPreCheckoutQuery(TelegramAnswerPreCheckoutQueryRequest telegramAnswerPreCheckoutQueryRequest);

}
