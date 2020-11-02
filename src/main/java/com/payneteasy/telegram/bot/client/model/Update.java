package com.payneteasy.telegram.bot.client.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Update {

    @SerializedName("update_id")
    private Integer updateId;

    @SerializedName("message")
    private Message message;

}
