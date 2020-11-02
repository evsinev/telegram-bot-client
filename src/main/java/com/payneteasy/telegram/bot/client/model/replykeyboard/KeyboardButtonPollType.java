package com.payneteasy.telegram.bot.client.model.replykeyboard;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class KeyboardButtonPollType {

    @SerializedName("type")
    private String type;

}
