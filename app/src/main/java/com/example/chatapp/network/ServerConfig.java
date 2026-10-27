package com.example.chatapp.network;

public final class ServerConfig {

    private static final String SERVER_HOST = "10.0.2.2";
    private static final int SERVER_PORT = 8081;

    public static final String BASE_URL =
            "http://" + SERVER_HOST + ":" + SERVER_PORT + "/";

    public static final String WEBSOCKET_URL =
            "ws://" + SERVER_HOST + ":" + SERVER_PORT + "/ws/chat";

    public static final int CONNECT_TIMEOUT_SECONDS = 10;
    public static final int READ_TIMEOUT_SECONDS = 30;
    public static final int WRITE_TIMEOUT_SECONDS = 30;

    private ServerConfig() {}
}