package com.payneteasy.telegram.bot.client.messages.invoice;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class TelegramAnswerPreCheckoutQueryRequest {

    @NonNull
    @SerializedName("pre_checkout_query_id")
    Long preCheckOutOrderId;

    @NonNull
    Boolean ok;

    @SerializedName("error_message")
    String errorMessage;

}
