package com.payneteasy.telegram.bot.client.model.invoice;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class SuccessfulPayment {

    private String currency;

    @SerializedName("total_amount")
    private Integer totalAmountCents;

    @SerializedName("invoice_payload")
    private String invoicePayload;

    @SerializedName("shipping_option_id")
    private String shippingOptionId;

    @SerializedName("order_info")
    private OrderInfo orderInfo;

    @SerializedName("telegram_payment_charge_id")
    private String telegramPaymentChargeId;

    @SerializedName("provider_payment_charge_id")
    private String providerPaymentChargeId;

}
