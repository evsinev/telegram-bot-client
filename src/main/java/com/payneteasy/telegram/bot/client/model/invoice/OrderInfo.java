package com.payneteasy.telegram.bot.client.model.invoice;

import com.google.gson.annotations.SerializedName;

public class OrderInfo {
    @SerializedName("name")
    private String userName;

    @SerializedName("phone_number")
    private String userPhoneNumber;

    @SerializedName("email")
    private String userEmail;

    @SerializedName("shipping_address")
    private ShippingAddress shippingAddress;

}
