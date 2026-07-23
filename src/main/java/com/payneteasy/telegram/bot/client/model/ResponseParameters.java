package com.payneteasy.telegram.bot.client.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

/**
 * Optional 'parameters' field returned by Telegram on some errors, used to automatically handle them.
 */
@Data
public class ResponseParameters {

    /**
     * The number of seconds left to wait before the request can be repeated (present on 429 Too Many Requests).
     */
    @SerializedName("retry_after")
    private Integer retryAfter;

    @SerializedName("migrate_to_chat_id")
    private Long migrateToChatId;
}
