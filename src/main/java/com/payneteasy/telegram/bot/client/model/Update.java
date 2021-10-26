package com.payneteasy.telegram.bot.client.model;

import com.google.gson.annotations.SerializedName;
import com.payneteasy.telegram.bot.client.model.invoice.PreCheckOutQueryMessage;
import lombok.Data;

@Data
public class Update {

    @SerializedName("update_id")
    private Integer updateId;

    @SerializedName("message")
    private Message message;

    @SerializedName("pre_checkout_query")
    private PreCheckOutQueryMessage preCheckOutQueryMessage;

}
