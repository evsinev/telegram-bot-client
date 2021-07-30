package com.payneteasy.telegram.bot.client.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Chat {

    @SerializedName("id")
    private Integer id;

    @SerializedName("type")
    private String type;

    @SerializedName("first_name")
    private String firstName;

    @SerializedName("last_name")
    private String lastName;

    @SerializedName("username")
    private String userName;

}
