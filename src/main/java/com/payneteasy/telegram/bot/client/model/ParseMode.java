package com.payneteasy.telegram.bot.client.model;

public enum ParseMode {

    /**
     * See https://core.telegram.org/bots/api#markdownv2-style
     */
    MarkdownV2,

    /**
     * See https://core.telegram.org/bots/api#html-style
     */
    HTML,

    /** This is a legacy mode, retained for backward compatibility **/
    Markdown
}
