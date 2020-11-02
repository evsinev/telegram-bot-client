package com.payneteasy.telegram.bot.client.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class Message {

    @SerializedName("message_id")
    private final Integer messageId;

    @SerializedName("from")
    private final User from;

    @SerializedName("date")
    private final Integer date;

    @SerializedName("chat")
    private final Chat chat;

    @SerializedName("text")
    private final String text;

    @SerializedName("entities")
    private final List<MessageEntity> entities;

    @SerializedName("contact")
    private final Contact contact;

    @SerializedName("reply_to_message")
    private final Message replyToMessage;

}
