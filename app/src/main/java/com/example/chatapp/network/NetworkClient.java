package com.example.chatapp.network;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public final class NetworkClient {

    private static final NetworkClient INSTANCE = new NetworkClient();

    private final SessionCookieJar cookieJar;
    private final OkHttpClient httpClient;

    private NetworkClient() {
        cookieJar = new SessionCookieJar();

        httpClient = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .connectTimeout(
                        ServerConfig.CONNECT_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                )
                .readTimeout(
                        ServerConfig.READ_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                )
                .writeTimeout(
                        ServerConfig.WRITE_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                )
                .callTimeout(45, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    public static NetworkClient getInstance() {
        return INSTANCE;
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    public void clearSession() {
        cookieJar.clear();
    }
}