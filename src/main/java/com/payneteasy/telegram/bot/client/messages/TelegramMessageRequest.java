package com.payneteasy.telegram.bot.client.messages;

import com.google.gson.annotations.SerializedName;
import com.payneteasy.telegram.bot.client.model.ParseMode;
import com.payneteasy.telegram.bot.client.model.replykeyboard.ReplyKeyboard;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TelegramMessageRequest implements IChatId {

    @SerializedName("chat_id")
    private final int chatId;

    private final String text;

    @SerializedName("parse_mode")
    private final ParseMode parseMode;

    @SerializedName("reply_markup")
    private final ReplyKeyboard replyMarkup;

    @Override
    public int getChatId() {
        return chatId;
    }

}
