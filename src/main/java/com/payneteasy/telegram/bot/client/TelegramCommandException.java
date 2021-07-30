package com.payneteasy.telegram.bot.client;

public class TelegramCommandException extends RuntimeException {

    private final String  id;
    private final Integer errorCode;

    public TelegramCommandException(String message, String id, Integer code) {
        super(message);
        this.id = id;
        this.errorCode = code;
    }

    public TelegramCommandException(String message, Throwable cause, String id, Integer code) {
        super(message, cause);
        this.id = id;
        this.errorCode = code;
    }

    public String getId() {
        return id;
    }

    public Integer getErrorCode() {
        return errorCode;
    }
}
