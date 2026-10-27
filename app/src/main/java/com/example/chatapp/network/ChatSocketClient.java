package com.example.chatapp.network;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public final class ChatSocketClient implements AutoCloseable {

    public interface Listener {

        // Refresh conversation history after each connection.
        void onReady();

        void onMessage(ApiMessage message);

        void onDisconnected(boolean unauthorized);

        void onError(String message);
    }

    private static final long HEARTBEAT_MS = 30_000;
    private static final long READY_TIMEOUT_MS = 15_000;

    private final String currentUser;
    private final Listener listener;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final Runnable reconnectTask = this::connect;
    private final Runnable heartbeatTask = this::sendHeartbeat;
    private final Runnable readyTimeoutTask = this::checkReadyTimeout;

    private WebSocket socket;
    private boolean ready;
    private boolean closed;
    private int retryAttempt;

    public ChatSocketClient(String currentUser, Listener listener) {
        if (currentUser == null
                || !currentUser.matches("[a-z0-9_]{3,24}")
                || listener == null) {
            throw new IllegalArgumentException(
                    "A valid username and listener are required."
            );
        }

        this.currentUser = currentUser;
        this.listener = listener;
    }

    public synchronized void connect() {
        if (closed || socket != null) {
            return;
        }

        handler.removeCallbacks(reconnectTask);
        ready = false;

        Request request = new Request.Builder()
                .url(ServerConfig.WEBSOCKET_URL)
                .build();

        socket = NetworkClient.getInstance()
                .getHttpClient()
                .newWebSocket(request, new SocketListener());

        handler.postDelayed(
                readyTimeoutTask,
                READY_TIMEOUT_MS
        );
    }

    private synchronized boolean isCurrent(WebSocket candidate) {
        return !closed && socket == candidate;
    }

    private void sendHeartbeat() {
        WebSocket active;

        synchronized (this) {
            if (closed || !ready || socket == null) {
                return;
            }

            active = socket;
        }

        if (!active.send("{\"type\":\"ping\"}")) {
            active.cancel();
            return;
        }

        synchronized (this) {
            if (isCurrent(active) && ready) {
                handler.postDelayed(
                        heartbeatTask,
                        HEARTBEAT_MS
                );
            }
        }
    }

    private void checkReadyTimeout() {
        WebSocket active;

        synchronized (this) {
            if (closed || ready) {
                return;
            }

            active = socket;
        }

        if (active != null) {
            active.cancel();
        }
    }

    private void disconnected(
            WebSocket candidate,
            boolean unauthorized
    ) {
        synchronized (this) {
            if (!isCurrent(candidate)) {
                return;
            }

            socket = null;
            ready = false;

            handler.removeCallbacks(heartbeatTask);
            handler.removeCallbacks(readyTimeoutTask);
            handler.removeCallbacks(reconnectTask);

            if (unauthorized) {
                closed = true;
            } else {
                long delay = Math.min(
                        30_000L,
                        1_000L << Math.min(retryAttempt++, 5)
                );

                handler.postDelayed(reconnectTask, delay);
            }
        }

        listener.onDisconnected(unauthorized);
    }

    @Override
    public void close() {
        WebSocket active;
        boolean wasReady;

        synchronized (this) {
            if (closed) {
                return;
            }

            closed = true;
            wasReady = ready;
            ready = false;

            active = socket;
            socket = null;

            handler.removeCallbacks(reconnectTask);
            handler.removeCallbacks(heartbeatTask);
            handler.removeCallbacks(readyTimeoutTask);
        }

        if (active != null) {
            if (wasReady) {
                active.close(1000, "Conversation closed");
            } else {
                active.cancel();
            }
        }
    }

    // Callbacks may arrive on background threads.
    private final class SocketListener extends WebSocketListener {

        @Override
        public void onMessage(
                @NonNull WebSocket webSocket,
                @NonNull String text
        ) {
            if (!isCurrent(webSocket)) {
                return;
            }

            if (text.length() > 16_384) {
                webSocket.close(1009, "Update too large");
                return;
            }

            try {
                JSONObject event = new JSONObject(text);
                String type = event.getString("type");

                if ("ready".equals(type)) {
                    if (!currentUser.equals(
                            event.getString("username")
                    )) {
                        disconnected(webSocket, true);
                        webSocket.close(1008, "Please log in again");
                        return;
                    }

                    synchronized (ChatSocketClient.this) {
                        if (!isCurrent(webSocket) || ready) {
                            return;
                        }

                        ready = true;
                        retryAttempt = 0;

                        handler.removeCallbacks(readyTimeoutTask);
                        handler.removeCallbacks(heartbeatTask);
                        handler.postDelayed(
                                heartbeatTask,
                                HEARTBEAT_MS
                        );
                    }

                    listener.onReady();

                } else if ("message".equals(type)) {
                    synchronized (ChatSocketClient.this) {
                        if (!isCurrent(webSocket) || !ready) {
                            return;
                        }
                    }

                    ApiMessage message = ApiMessage.fromJson(
                            event.getJSONObject("message")
                    );

                    if (!currentUser.equals(message.getSender())
                            && !currentUser.equals(
                            message.getReceiver()
                    )) {
                        disconnected(webSocket, true);
                        webSocket.close(1008, "Invalid recipient");
                        return;
                    }

                    listener.onMessage(message);

                } else if ("error".equals(type)) {
                    listener.onError(
                            event.optString(
                                    "error",
                                    "A live update could not be processed."
                            )
                    );
                }
            } catch (JSONException | IOException exception) {
                listener.onError(
                        "The server sent an invalid live update."
                );
                webSocket.close(1002, "Invalid update");
            }
        }

        @Override
        public void onMessage(
                @NonNull WebSocket webSocket,
                @NonNull ByteString bytes
        ) {
            if (isCurrent(webSocket)) {
                webSocket.close(1003, "Text updates only");
            }
        }

        @Override
        public void onClosing(
                @NonNull WebSocket webSocket,
                int code,
                @NonNull String reason
        ) {
            webSocket.close(code, reason);

            if (code == 1008) {
                disconnected(webSocket, true);
            }
        }

        @Override
        public void onClosed(
                @NonNull WebSocket webSocket,
                int code,
                @NonNull String reason
        ) {
            disconnected(webSocket, code == 1008);
        }

        @Override
        public void onFailure(
                @NonNull WebSocket webSocket,
                @NonNull Throwable throwable,
                @Nullable Response response
        ) {
            boolean unauthorized = response != null
                    && (response.code() == 401
                    || response.code() == 403);

            if (response != null) {
                response.close();
            }

            disconnected(webSocket, unauthorized);
        }
    }
}