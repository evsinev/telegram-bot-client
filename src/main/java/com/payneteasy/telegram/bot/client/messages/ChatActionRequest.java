package com.payneteasy.telegram.bot.client.messages;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
public class ChatActionRequest {

    public enum Action {
        typing, upload_photo, record_video, upload_video, record_voice, upload_voice, upload_document, find_location, record_video_note, upload_video_note
    }

    /**
     * Unique identifier for the target chat
     */
    @SerializedName("chat_id")
    private final Long chatId;

//    /**
//     * username of the target channel (in the format @channelusername)
//     */
//    @SerializedName("chat_id")
//    private final Integer username;

    /**
     * Type of action to broadcast.
     */
    @NonNull
    private final Action action;

}
