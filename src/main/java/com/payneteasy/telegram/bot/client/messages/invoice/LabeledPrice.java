package com.payneteasy.telegram.bot.client.messages.invoice;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NonNull;

@Data
public class LabeledPrice {
    @NonNull
    private String label;

    @NonNull
    @SerializedName("amount")
    private Integer amountCents;
}
