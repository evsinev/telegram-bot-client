package com.payneteasy.telegram.bot.client.model.replykeyboard;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;

import java.util.List;

@Builder
public class ReplyKeyboardMarkup implements ReplyKeyboard {

    @SerializedName("keyboard")
    private final List<List<KeyboardButton>> keyboard;

    @SerializedName("resize_keyboard")
    private final boolean resizeKeyboard;

    @SerializedName("one_time_keyboard")
    private final boolean oneTimeKeyboard;

    @SerializedName("selective")
    private final boolean selective;

}
