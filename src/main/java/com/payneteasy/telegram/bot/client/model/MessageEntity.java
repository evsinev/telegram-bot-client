package com.payneteasy.telegram.bot.client.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class MessageEntity {

    @SerializedName("type")
    private String type;

    @SerializedName("offset")
    private Integer offset;

    @SerializedName("length")
    private Integer length;

    @SerializedName("url")
    private String url;

    @SerializedName("user")
    private User user;

    @SerializedName("language")
    private String language;

}
