package com.payneteasy.telegram.bot.client.model.replykeyboard;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;

@Builder
public class KeyboardButton {

    @SerializedName("text")
    private final String text;

    @SerializedName("request_contact")
    private final Boolean requestContact;

    @SerializedName("request_location")
    private final Boolean requestLocation;

    @SerializedName("request_poll")
    private final KeyboardButtonPollType requestPoll;

}
