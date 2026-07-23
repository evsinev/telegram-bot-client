package com.payneteasy.telegram.bot.client.messages.invoice;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class LabeledPrice {

    @NonNull
    String label;

    @NonNull
    Integer amount;
}
