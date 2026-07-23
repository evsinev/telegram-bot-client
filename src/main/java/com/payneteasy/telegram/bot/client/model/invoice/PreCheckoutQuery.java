package com.payneteasy.telegram.bot.client.model.invoice;

import com.google.gson.annotations.SerializedName;
import com.payneteasy.telegram.bot.client.model.User;
import lombok.Data;

@Data
public class PreCheckoutQuery {
    private String id;

    @SerializedName("from")
    private User from;

    private String currency;

    @SerializedName("total_amount")
    private Integer totalAmount;

    @SerializedName("invoice_payload")
    private String invoicePayload;
}
