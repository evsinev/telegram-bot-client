package com.payneteasy.telegram.bot.client.http;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class SimpleHttpClient implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleHttpClient.class);

    private HttpURLConnection connection;
    private InputStream       inputStream;

    public void connect(String aUrl, int aConnectionTimeoutMs, int aReadTimeoutMs, String aMethod) throws IOException {
        LOG.debug("Sending {} to {} with {}ms, {}ms ...", aMethod, aUrl, aConnectionTimeoutMs, aReadTimeoutMs);
        connection = (HttpURLConnection) new URL(aUrl).openConnection();
        connection.setReadTimeout(aReadTimeoutMs);
        connection.setConnectTimeout(aConnectionTimeoutMs);
        connection.setRequestMethod(aMethod);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
    }

    @Override
    public void close() throws IOException {
        if (inputStream != null) {
            inputStream.close();
        }
    }

    public void sendHeader(String aName, String aValue) {
        connection.setRequestProperty(aName, aValue);
    }

    public SimpleHttpResponse fetchResponse() throws IOException {
        int responseCode = connection.getResponseCode();
        inputStream = responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        int    length   = connection.getHeaderFieldInt("Content-Length", 0);
        LOG.debug("Fetching response with {} status and {} length", responseCode, length);
        byte[] response = IOUtils.readFully(inputStream, length);
        return new SimpleHttpResponse(responseCode, connection.getResponseMessage(), response);
    }

    public void sendBody(byte[] aBody) throws IOException {
        connection.setDoOutput(true);
        connection.getOutputStream().write(aBody);
    }
}
