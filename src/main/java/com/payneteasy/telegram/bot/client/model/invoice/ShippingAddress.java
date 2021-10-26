package com.payneteasy.telegram.bot.client.model.invoice;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class ShippingAddress {

    @SerializedName("country_code")
    private String countryCode;

    private String state;

    private String city;

    @SerializedName("street_line1")
    private String streetLine1;

    @SerializedName("street_line2")
    private String streetLine2;

    @SerializedName("post_code")
    private String postCode;

}
