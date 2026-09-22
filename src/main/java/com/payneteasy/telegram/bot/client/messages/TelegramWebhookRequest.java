package com.payneteasy.telegram.bot.client.messages;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TelegramWebhookRequest {

    private final String url;

    /**
     * Optional. Telegram sends it back in the {@code X-Telegram-Bot-Api-Secret-Token} header of
     * every update, which is what lets the receiving side authenticate an update without a token
     * in the webhook address.
     */
    @SerializedName("secret_token")
    private final String secretToken;

    /**
     * Kept explicitly: callers that do not set a secret must keep compiling and must keep sending
     * exactly the body they sent before ({@code null} is not serialized by Gson).
     */
    public TelegramWebhookRequest(String url) {
        this(url, null);
    }

}
