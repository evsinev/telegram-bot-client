package com.payneteasy.telegram.bot.client;

public class TelegramCommandException extends RuntimeException {

    private final String  id;
    private final Integer errorCode;
    private final Integer retryAfter;

    public TelegramCommandException(String message, String id, Integer code) {
        this(message, id, code, null);
    }

    public TelegramCommandException(String message, String id, Integer code, Integer retryAfter) {
        super(message);
        this.id = id;
        this.errorCode = code;
        this.retryAfter = retryAfter;
    }

    public TelegramCommandException(String message, Throwable cause, String id, Integer code) {
        super(message, cause);
        this.id = id;
        this.errorCode = code;
        this.retryAfter = null;
    }

    public String getId() {
        return id;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    /**
     * @return seconds to wait before retrying (from Telegram's parameters.retry_after on 429), or null.
     */
    public Integer getRetryAfter() {
        return retryAfter;
    }
}
