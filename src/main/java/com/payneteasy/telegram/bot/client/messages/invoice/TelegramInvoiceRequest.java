package com.payneteasy.telegram.bot.client.messages.invoice;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.Set;

@Value
@Builder(toBuilder = true)
public class TelegramInvoiceRequest {

    @NonNull
    @SerializedName("chat_id")
    Integer chatId;

    @NonNull
    String title;

    @NonNull
    String description;

    @NonNull
    String payload;

    @NonNull
    @SerializedName("provider_token")
    String paymentProviderToken;

    @NonNull
    String currency;

    @NonNull
    @SerializedName("prices")
    Set<LabeledPrice> labeledPrices;

    @SerializedName("max_tip_amount")
    Integer maxTipAmount;

    @SerializedName("suggested_tip_amounts")
    Integer[] suggestedTipAmounts;

    @SerializedName("start_parameter")
    String startParameter;

    @SerializedName("provider_data")
    String providerData;

    @SerializedName("photo_url")
    String photoUrl;

    @SerializedName("photo_size")
    Integer photoSize;

    @SerializedName("photo_width")
    Integer photoWidth;

    @SerializedName("photo_height")
    Integer photoHeight;

    @SerializedName("need_name")
    Boolean needName;

    @SerializedName("need_phone_number")
    Boolean needPhoneNumber;

    @SerializedName("need_email")
    Boolean needEmail;

    @SerializedName("need_shipping_address")
    Boolean needShippingAddress;

    @SerializedName("send_phone_number_to_provider")
    Boolean sendPhoneNumberToProvider;

    @SerializedName("send_email_to_provider")
    Boolean sendEmailToProvider;

    @SerializedName("is_flexible")
    Boolean isFlexible;

    @SerializedName("disable_notification")
    Boolean disableNotification;

    @SerializedName("reply_to_message_id")
    Integer replyToMessageId;

    @SerializedName("allow_sending_without_reply")
    Boolean allowSendingWithoutReply;

    //TODO: reply_markup
}
