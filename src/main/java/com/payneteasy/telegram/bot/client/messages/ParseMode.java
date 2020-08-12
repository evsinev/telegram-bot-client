package com.payneteasy.telegram.bot.client.messages;

public enum ParseMode {
    HTML("HTML")
    , MARKDOWN_V2("MarkdownV2");

    public final String label;

    private ParseMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
