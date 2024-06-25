package com.payneteasy.telegram.bot.client.model;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;

/**
 * Options for link preview.
 */
@Data
@Builder
public class LinkPreviewOptions {

    /**
     * True, if the link preview is disabled.
     */
    @SerializedName("is_disabled")
    private Boolean isDisabled;

    /**
     * URL to use for the link preview. If empty, then the first URL found in the message text will be used.
     */
    @SerializedName("url")
    private String url;

    /**
     * True, if the media in the link preview is supposed to be shrunk; ignored if the URL isn't explicitly specified or media size change isn't supported for the preview.
     */
    @SerializedName("prefer_small_media")
    private Boolean preferSmallMedia;

    /**
     * True, if the media in the link preview is supposed to be enlarged; ignored if the URL isn't explicitly specified or media size change isn't supported for the preview.
     */
    @SerializedName("prefer_large_media")
    private Boolean preferLargeMedia;

    /**
     * True, if the link preview must be shown above the message text; otherwise, the link preview will be shown below the message text.
     */
    @SerializedName("show_above_text")
    private Boolean showAboveText;
}
