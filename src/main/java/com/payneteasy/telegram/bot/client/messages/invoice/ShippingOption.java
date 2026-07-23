package com.payneteasy.telegram.bot.client.messages.invoice;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class ShippingOption {

    @NonNull
    String id;

    @NonNull
    String title;

    @NonNull
    List<LabeledPrice> prices;

}
