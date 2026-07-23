package com.payneteasy.telegram.bot.client.messages.invoice;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NonNull;

import java.util.Set;

@Data
public class ShippingOption {
    @NonNull
    private String id;

    @NonNull
    private String title;

    @NonNull
    private Set<LabeledPrice> prices;

}
