package com.payneteasy.telegram.bot.client.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Contact {

    @SerializedName("phone_number")
    private final String phoneNumber;

    @SerializedName("first_name")
    private final String firstName;

    @SerializedName("last_name")
    private final String lastName;

    @SerializedName("user_id")
    private final Long userID;

    @SerializedName("vcard")
    private final String vCard;

}
