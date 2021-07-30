package com.payneteasy.telegram.bot.client.messages;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class TelegramStandardResponse {

    /**
     * If 'ok' equals true, the request was successful and the result of the query can be found in the 'result' field
     *
     * In case of an unsuccessful request, 'ok' equals false and the error is explained in the 'description'
     */
    private final boolean ok;

    /**
     * an optional String field 'description' with a human-readable description of the result
     */
    private final String description;

    /**
     * An Integer 'error_code' field is also returned, but its contents are subject to change in the future.
     *
     * Some errors may also have an optional field 'parameters' of the type ResponseParameters, which can help to automatically handle the error.
     */
    @SerializedName("error_code")
    private final Integer errorCode;

}
