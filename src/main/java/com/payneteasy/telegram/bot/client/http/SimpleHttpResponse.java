package com.payneteasy.telegram.bot.client.http;

import lombok.Data;

@Data
public class SimpleHttpResponse {

    private final int    statusCode;
    private final String reasonPhrase;
    private final byte[] body;

}
