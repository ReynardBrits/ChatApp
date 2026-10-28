package com.example.chatapp.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class JavaChatClient extends Endpoint
        implements AutoCloseable {

    private static final URI SOCKET_URI =
            URI.create("ws://127.0.0.1:8081/ws/chat");

    private static final Gson GSON = new GsonBuilder()
            .setStrictness(Strictness.STRICT)
            .create();

    private final String username;
    private final Consumer<JsonObject> eventListener;

    private final AtomicBoolean started = new AtomicBoolean();
    private final CountDownLatch readySignal = new CountDownLatch(1);

    private final Object sendLock = new Object();

    private final ScheduledExecutorService heartbeat =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "java-chat-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    private volatile Session session;
    private volatile boolean ready;
    private volatile boolean closed;

    // Events are delivered on WebSocket background threads.
    public JavaChatClient(
            String username,
            Consumer<JsonObject> eventListener
    ) {
        if (username == null
                || !username.matches("[a-z0-9_]{3,24}")) {
            throw new IllegalArgumentException("Invalid username.");
        }

        this.username = username;
        this.eventListener = Objects.requireNonNull(eventListener);
    }

    public void connect(String sessionCookie)
            throws DeploymentException, IOException,
            InterruptedException {

        if (sessionCookie == null
                || !sessionCookie.matches(
                "CHATAPPSESSION=[A-Za-z0-9._~-]{1,256}"
        )) {
            throw new IllegalArgumentException(
                    "A valid login session cookie is required."
            );
        }

        if (closed || !started.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "Create a new client for each connection."
            );
        }

        ClientEndpointConfig configuration =
                ClientEndpointConfig.Builder.create()
                        .configurator(
                                new ClientEndpointConfig.Configurator() {
                                    @Override
                                    public void beforeRequest(
                                            Map<String, List<String>> headers
                                    ) {
                                        headers.put(
                                                "Cookie",
                                                List.of(sessionCookie)
                                        );
                                    }
                                }
                        )
                        .build();

        configuration.getUserProperties().put(
                "org.apache.tomcat.websocket.IO_TIMEOUT_MS",
                "10000"
        );

        configuration.getUserProperties().put(
                "org.apache.tomcat.websocket.MAX_REDIRECTIONS",
                "0"
        );

        try {
            WebSocketContainer container =
                    ContainerProvider.getWebSocketContainer();

            container.setDefaultMaxTextMessageBufferSize(16_384);

            container.connectToServer(
                    this,
                    configuration,
                    SOCKET_URI
            );

            if (!readySignal.await(15, TimeUnit.SECONDS)
                    || !isConnected()) {
                throw new IOException(
                        "The server did not confirm the chat connection."
                );
            }

            heartbeat.scheduleAtFixedRate(
                    this::sendHeartbeat,
                    30,
                    30,
                    TimeUnit.SECONDS
            );

        } catch (DeploymentException | IOException
                 | InterruptedException | RuntimeException exception) {
            close();
            throw exception;
        }
    }

    @Override
    public void onOpen(Session opened, EndpointConfig configuration) {
        synchronized (this) {
            if (!closed) {
                session = opened;
            }
        }

        if (closed) {
            closeSocket(opened);
            return;
        }

        opened.setMaxTextMessageBufferSize(16_384);

        opened.getUserProperties().put(
                "org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT",
                5_000L
        );

        MessageHandler.Whole<String> handler = this::handleText;
        opened.addMessageHandler(String.class, handler);
    }

    private void handleText(String text) {
        if (closed) {
            return;
        }

        try {
            JsonObject event = GSON.fromJson(text, JsonObject.class);

            if (event == null || !event.has("type")) {
                throw new IllegalArgumentException("Invalid chat event.");
            }

            String type = event.get("type").getAsString();

            switch (type) {
                case "ready" -> {
                    if (!username.equals(
                            event.get("username").getAsString()
                    )) {
                        throw new IllegalArgumentException(
                                "The connected account does not match."
                        );
                    }

                    ready = true;
                    readySignal.countDown();
                    eventListener.accept(event);
                }

                case "message" -> {
                    if (ready) {
                        eventListener.accept(event);
                    }
                }

                case "error" -> eventListener.accept(event);

                case "pong" -> {
                    // The heartbeat was acknowledged.
                }

                default -> throw new IllegalArgumentException(
                        "Unknown chat event."
                );
            }

        } catch (RuntimeException exception) {
            System.err.println("Unable to process a chat update.");
            close();
        }
    }

    public boolean isConnected() {
        Session active = session;

        return !closed && ready
                && active != null && active.isOpen();
    }

    public void sendText(String receiver, String content)
            throws IOException {

        String recipient = receiver == null
                ? ""
                : receiver.trim().toLowerCase(Locale.ROOT);

        String message = content == null ? "" : content.trim();

        if (!recipient.matches("[a-z0-9_]{3,24}")
                || recipient.equals(username)) {
            throw new IllegalArgumentException(
                    "Choose another registered user."
            );
        }

        if (message.isEmpty() || message.length() > 2000) {
            throw new IllegalArgumentException(
                    "Messages must contain 1 to 2000 characters."
            );
        }

        sendJson(Map.of(
                "type", "message",
                "receiver", recipient,
                "content", message
        ));
    }

    private void sendHeartbeat() {
        try {
            sendJson(Map.of("type", "ping"));
        } catch (IOException exception) {
            close();
        }
    }

    private void sendJson(Object event) throws IOException {
        synchronized (sendLock) {
            Session active = session;

            if (!isConnected() || active == null) {
                throw new IOException("The chat connection is closed.");
            }

            try {
                active.getBasicRemote().sendText(GSON.toJson(event));
            } catch (IllegalStateException exception) {
                throw new IOException(
                        "The message could not be confirmed.",
                        exception
                );
            }
        }
    }

    @Override
    public void onClose(Session socket, CloseReason reason) {
        close();

        System.out.println(
                "Chat connection closed ("
                        + reason.getCloseCode().getCode() + ")."
        );
    }

    @Override
    public void onError(Session socket, Throwable error) {
        System.err.println("The Java chat connection failed.");
        close();
    }

    @Override
    public void close() {
        Session active;

        synchronized (this) {
            if (closed) {
                return;
            }

            closed = true;
            ready = false;

            active = session;
            session = null;
        }

        readySignal.countDown();
        heartbeat.shutdownNow();
        closeSocket(active);
    }

    private static void closeSocket(Session socket) {
        if (socket == null) {
            return;
        }

        try {
            if (socket.isOpen()) {
                socket.close();
            }
        } catch (IOException | IllegalStateException exception) {
            // The connection may already have closed.
        }
    }
}