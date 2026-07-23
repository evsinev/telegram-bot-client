package com.payneteasy.telegram.bot.client.model;

import com.google.gson.annotations.SerializedName;
import com.payneteasy.telegram.bot.client.model.invoice.PreCheckoutQuery;
import com.payneteasy.telegram.bot.client.model.invoice.ShippingQuery;
import lombok.Data;

@Data
public class Update {

    @SerializedName("update_id")
    private Long updateId;

    @SerializedName("message")
    private Message message;

    @SerializedName("pre_checkout_query")
    private PreCheckoutQuery preCheckoutQuery;

    @SerializedName("shipping_query")
    private ShippingQuery shippingQuery;

}
