package com.payneteasy.telegram.bot.client.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class User {

    @SerializedName("id")
    private Long id;

    @SerializedName("first_name")
    private String firstName;

    @SerializedName("is_bot")
    private Boolean isBot;

    @SerializedName("last_name")
    private String lastName;

    @SerializedName("username")
    private String userName;

    @SerializedName("language_code")
    private String languageCode;

    @SerializedName("can_join_groups")
    private Boolean canJoinGroups;

    @SerializedName("can_read_all_group_messages")
    private Boolean canReadAllGroupMessages;

    @SerializedName("supports_inline_queries")
    private Boolean supportInlineQueries;

}
