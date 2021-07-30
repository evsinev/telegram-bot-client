package com.payneteasy.telegram.bot.client.http;

import com.payneteasy.telegram.bot.client.TelegramCommandException;

public interface ITelegramHttpClient {

    <T> T get(String aMethodName, Class<T> aResponseClass) throws TelegramCommandException;

    <R,T> T post(String aMethodName, R aRequest, Class<T> aResponseClass) throws TelegramCommandException;

    <R> void post(String aMethodName, R aRequest) throws TelegramCommandException;
}
