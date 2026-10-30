package com.example.chatapp.network;

public final class ServerConfig {

    private static final String SERVER_HOST =
            "chatapp-l3zc.onrender.com";

    public static final String BASE_URL =
            "https://" + SERVER_HOST + "/";

    public static final String WEBSOCKET_URL =
            "wss://" + SERVER_HOST + "/ws/chat";

    public static final int CONNECT_TIMEOUT_SECONDS = 10;
    public static final int READ_TIMEOUT_SECONDS = 30;
    public static final int WRITE_TIMEOUT_SECONDS = 30;

    private ServerConfig() {}
}