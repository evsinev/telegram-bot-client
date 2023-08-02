package com.payneteasy.telegram.bot.client;

import com.payneteasy.telegram.bot.client.messages.*;
import com.payneteasy.telegram.bot.client.model.TelegramMessage;
import com.payneteasy.telegram.bot.client.model.TelegramUser;

public interface ITelegramService {

    TelegramUser getMe();

    TelegramMessage sendMessage(TelegramMessageRequest aRequest);

    void setWebhook(TelegramWebhookRequest aRequest);

    void clearWebhook();

    void setMyCommands(TelegramSetMyCommandsRequest aMyCommands);

    void sendChatAction(ChatActionRequest aChatActionRequest) ;

    /**
     *
     * Use this method to receive incoming updates using long polling
     *
     * 1. This method will not work if an outgoing webhook is set up.
     * 
     * 2. In order to avoid getting duplicate updates, recalculate offset after each server response.
     *
     * @return An Array of Update objects is returned
     */
    TelegramGetUpdatesResponse getUpdates(TelegramGetUpdatesRequest aRequest);

    /**
     * Use this method to edit text and game messages.
     *
     * On success, if the edited message is not an inline message,
     * the edited Message is returned, otherwise True is returned
     */
    TelegramMessage editMessageText(EditMessageTextRequest aRequest);

    /**
     * Use this method to change the bot's description,
     * which is shown in the chat with the bot if the chat is empty.
     *
     * @param aRequest description info
     */
    void setMyDescription(SetMyDescriptionRequest aRequest);

}
