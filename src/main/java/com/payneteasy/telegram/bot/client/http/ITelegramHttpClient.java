package com.payneteasy.telegram.bot.client.http;

public interface ITelegramHttpClient {

    <T> T get(String aMethodName, Class<T> aResponseClass);

    <R,T> T post(String aMethodName, R aRequest, Class<T> aResponseClass);
}
