package com.payneteasy.telegram.bot.client.messages;

import com.google.gson.annotations.SerializedName;
import com.payneteasy.telegram.bot.client.model.ParseMode;
import com.payneteasy.telegram.bot.client.model.replykeyboard.ReplyKeyboard;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Builder
@Data
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class EditMessageTextRequest {

    /**
     * Required if inline_message_id is not specified.
     *
     * Unique identifier for the target chat or username of the target channel (in the format @channelusername)
     */
    @SerializedName("chat_id")
    Long chatId;


    /**
     * Required if inline_message_id is not specified. Identifier of the message to edit
     */
    @SerializedName("message_id")
    Long messageId;

    /**
     * Required if chat_id and message_id are not specified. Identifier of the inline message
     */
    @SerializedName("inline_message_id")
    String inlineMessageId;

    /**
     * New text of the message, 1-4096 characters after entities parsing
     */
    String text;

    /**
     * Mode for parsing entities in the message text.
     * See formatting options for more details
     */
    @SerializedName("parse_mode")
    ParseMode parseMode;

    /**
     * Disables link previews for links in this message
     */
    @SerializedName("disable_web_page_preview")
    Boolean disableWebPagePreview;

    /**
     * A JSON-serialized object for an inline keyboard.
     */
    @SerializedName("reply_markup")
    ReplyKeyboard replyMarkup;
}
