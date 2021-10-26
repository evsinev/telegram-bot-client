package com.payneteasy.telegram.bot.client.model.invoice;

import com.google.gson.annotations.SerializedName;
import com.payneteasy.telegram.bot.client.model.User;
import lombok.Data;

@Data
public class PreCheckOutQueryMessage {
    private Long id;

    @SerializedName("from")
    private User from;

    private String currency;

    @SerializedName("total_amount")
    private Integer totalAmountCents;

    @SerializedName("invoice_payload")
    private String invoicePayload;
}
