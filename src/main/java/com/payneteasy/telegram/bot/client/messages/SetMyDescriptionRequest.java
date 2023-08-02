package com.payneteasy.telegram.bot.client.messages;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Data
@FieldDefaults(makeFinal = true, level = PRIVATE)
@Builder
public class SetMyDescriptionRequest {

    /**
     * New bot description; 0-512 characters.
     *
     * Pass an empty string to remove the dedicated description for the given language.
     */
    @SerializedName("description")
    String description;

    /**
     * A two-letter ISO 639-1 language code.
     *
     * If empty, the description will be applied to all users for whose language
     * there is no dedicated description.
     */
    @SerializedName("language_code")
    String languageCode;



}
