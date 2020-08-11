package com.payneteasy.telegram.bot.client.http;

import lombok.Data;

@Data
public class HttpClientTimeouts {

    private final int connectionMs;
    private final int readMs;
    private final int writeMs;

}
