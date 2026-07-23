package com.payneteasy.telegram.bot.client.model.invoice;

import com.google.gson.annotations.SerializedName;
import com.payneteasy.telegram.bot.client.model.User;
import lombok.Data;

@Data
public class ShippingQueryMessage {
    private Long id;

    @SerializedName("from")
    private User from;

    @SerializedName("invoice_payload")
    private String invoicePayload;

    @SerializedName("shipping_address")
    private ShippingAddress shippingAddress;

}
