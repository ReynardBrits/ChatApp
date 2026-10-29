package com.example.chatapp.server;

import com.google.gson.Gson;

import javax.servlet.http.HttpSession;
import javax.websocket.CloseReason;
import javax.websocket.Session;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ChatHub {

    private static final int MAX_PENDING_UPDATES = 64;
    private static final long SEND_TIMEOUT_MS = 5000;

    private static final Gson GSON = new Gson();

    private final ConcurrentMap<String, Client> clients =
            new ConcurrentHashMap<>();

    private volatile PushNotificationService pushNotifications;

    public void setPushNotifications(
            PushNotificationService pushNotifications
    ) {
        this.pushNotifications = pushNotifications;
    }
    public boolean register(
            Session socket,
            HttpSession httpSession
    ) {
        UserRepository.Account account =
                ChatSocketConfigurator.currentAccount(httpSession);

        if (account == null || !socket.isOpen()) {
            disconnect(
                    socket,
                    CloseReason.CloseCodes.VIOLATED_POLICY,
                    "Please log in again."
            );
            return false;
        }

        socket.getAsyncRemote().setSendTimeout(SEND_TIMEOUT_MS);

        clients.put(
                socket.getId(),
                new Client(socket, httpSession, account)
        );

        return true;
    }

    public UserRepository.Account currentAccount(Session socket) {
        Client client = clients.get(socket.getId());

        if (client == null) {
            return null;
        }

        if (!client.isAuthenticated()) {
            disconnect(
                    socket,
                    CloseReason.CloseCodes.VIOLATED_POLICY,
                    "Please log in again."
            );
            return null;
        }

        return client.account;
    }

    public void unregister(Session socket) {
        if (socket == null) {
            return;
        }

        Client client = clients.remove(socket.getId());

        if (client != null) {
            client.stop();
        }
    }

    public void send(Session socket, Object event) {
        Client client = clients.get(socket.getId());

        if (client != null) {
            client.enqueue(GSON.toJson(event));
        }
    }

    public void publish(MessageRepository.ChatMessage message) {
        String json = GSON.toJson(Map.of(
                "type", "message",
                "message", message
        ));

        for (Client client : clients.values()) {
            String username = client.account.username();

            boolean participant =
                    username.equals(message.sender())
                            || username.equals(message.receiver());

            if (participant) {
                client.enqueue(json);
            }
        }

        PushNotificationService notifications = pushNotifications;

        if (notifications != null) {
            notifications.notifyNewMessage(message);
        }
    }

    public void broadcast(Object event) {
        String json = GSON.toJson(event);

        for (Client client : clients.values()) {
            client.enqueue(json);
        }
    }

    public void disconnect(
            Session socket,
            CloseReason.CloseCode code,
            String reason
    ) {
        if (socket == null) {
            return;
        }

        unregister(socket);

        try {
            if (socket.isOpen()) {
                socket.close(new CloseReason(code, reason));
            }
        } catch (IOException | IllegalStateException exception) {
            // The connection may already have closed.
        }
    }

    private final class Client {

        private final Session socket;
        private final HttpSession httpSession;
        private final UserRepository.Account account;

        private final Deque<String> pending = new ArrayDeque<>();

        private boolean sending;
        private boolean stopped;

        private Client(
                Session socket,
                HttpSession httpSession,
                UserRepository.Account account
        ) {
            this.socket = socket;
            this.httpSession = httpSession;
            this.account = account;
        }

        private boolean isAuthenticated() {
            return socket.isOpen()
                    && account.equals(
                    ChatSocketConfigurator.currentAccount(httpSession)
            );
        }

        private void enqueue(String json) {
            if (!isAuthenticated()) {
                disconnect(
                        socket,
                        CloseReason.CloseCodes.VIOLATED_POLICY,
                        "Please log in again."
                );
                return;
            }

            boolean overflow;
            boolean startSending;

            synchronized (this) {
                if (stopped) {
                    return;
                }

                overflow = pending.size() >= MAX_PENDING_UPDATES;
                startSending = !sending && !overflow;

                if (!overflow) {
                    pending.addLast(json);
                    sending = true;
                }
            }

            if (overflow) {
                disconnect(
                        socket,
                        CloseReason.CloseCodes.TRY_AGAIN_LATER,
                        "Too many pending updates. Please reconnect."
                );
            } else if (startSending) {
                sendNext();
            }
        }

        private void sendNext() {
            if (!isAuthenticated()) {
                disconnect(
                        socket,
                        CloseReason.CloseCodes.VIOLATED_POLICY,
                        "Please log in again."
                );
                return;
            }

            String json;

            synchronized (this) {
                if (stopped) {
                    return;
                }

                json = pending.peekFirst();

                if (json == null) {
                    sending = false;
                    return;
                }
            }

            try {
                socket.getAsyncRemote().sendText(json, result -> {
                    if (!result.isOK()) {
                        disconnect(
                                socket,
                                CloseReason.CloseCodes.UNEXPECTED_CONDITION,
                                "Unable to deliver updates. Please reconnect."
                        );
                        return;
                    }

                    synchronized (this) {
                        if (stopped) {
                            return;
                        }

                        pending.pollFirst();
                    }

                    sendNext();
                });

            } catch (RuntimeException exception) {
                disconnect(
                        socket,
                        CloseReason.CloseCodes.UNEXPECTED_CONDITION,
                        "Unable to deliver updates. Please reconnect."
                );
            }
        }

        private synchronized void stop() {
            stopped = true;
            pending.clear();
            sending = false;
        }
    }
}