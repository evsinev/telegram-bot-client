package com.payneteasy.telegram.bot.client.messages;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

import static lombok.AccessLevel.PRIVATE;

@Data
@FieldDefaults(makeFinal = true, level = PRIVATE)
@Builder
public class TelegramGetUpdatesRequest {
    /**
     * Identifier of the first update to be returned.
     *
     * Must be greater by one than the highest among the identifiers of previously received updates.
     *
     * By default, updates starting with the earliest unconfirmed update are returned.
     *
     * An update is considered confirmed as soon as getUpdates is called with an offset higher than its update_id.
     *
     * The negative offset can be specified to retrieve updates starting from -offset update from the end of the updates queue.
     *
     * All previous updates will forgotten.
     */
    Integer offset;

    /**
     * Limits the number of updates to be retrieved.
     *
     * Values between 1-100 are accepted.
     *
     * Defaults to 100.
     */
    Integer limit;

    /**
     * Timeout in seconds for long polling.
     *
     * Defaults to 0, i.e. usual short polling.
     *
     * Should be positive, short polling should be used for testing purposes only.
     *
     */
    Integer timeout;

    /**
     * A JSON-serialized list of the update types you want your bot to receive.
     *
     * For example, specify [“message”, “edited_channel_post”, “callback_query”] to only receive updates of these types.
     *
     * See Update for a complete list of available update types.
     *
     * Specify an empty list to receive all update types except chat_member (default).
     *
     * If not specified, the previous setting will be used.
     *
     * Please note that this parameter doesn't affect updates created before the call to the getUpdates,
     * so unwanted updates may be received for a short period of time.
     */
    @SerializedName("allowed_updates")
    List<String> allowedUpdates;
}
