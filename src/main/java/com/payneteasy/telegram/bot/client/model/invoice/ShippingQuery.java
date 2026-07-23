package com.payneteasy.telegram.bot.client.model.invoice;

import com.google.gson.annotations.SerializedName;
import com.payneteasy.telegram.bot.client.model.User;
import lombok.Data;

@Data
public class ShippingQuery {
    private String id;

    @SerializedName("from")
    private User from;

    @SerializedName("invoice_payload")
    private String invoicePayload;

    @SerializedName("shipping_address")
    private ShippingAddress shippingAddress;

}
