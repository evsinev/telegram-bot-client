package com.payneteasy.telegram.bot.client.messages;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class TelegramMessageRequest {

    @SerializedName("chat_id")
    private final int chatId;

    private final String text;

    @SerializedName("parse_mode")
    private final String parseMode;

}
