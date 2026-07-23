package com.payneteasy.telegram.bot.client.messages.invoice;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class TelegramAnswerShippingQueryRequest {

    @NonNull
    @SerializedName("shipping_query_id")
    String shippingQueryId;

    @NonNull
    Boolean ok;

    @SerializedName("shipping_options")
    List<ShippingOption> shippingOptions;

    @SerializedName("error_message")
    String errorMessage;
}
