package com.example.chatapp.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;

import javax.servlet.http.HttpSession;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class RoomSocketEndpoint extends Endpoint {

    public static final String HUB_PROPERTY =
            RoomSocketEndpoint.class.getName() + ".hub";

    private static final int MAX_FRAME_LENGTH = 16384;
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final long IDLE_TIMEOUT_MS = 5 * 60 * 1000L;

    private static final Pattern CLIENT_ID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
    );

    private static final Logger LOGGER =
            Logger.getLogger(RoomSocketEndpoint.class.getName());

    private static final Gson GSON = new GsonBuilder()
            .setStrictness(Strictness.STRICT)
            .create();

    private ChatHub hub;

    @Override
    public void onOpen(Session socket, EndpointConfig config) {
        Object hubValue = config.getUserProperties().get(HUB_PROPERTY);

        if (!(hubValue instanceof ChatHub roomHub)) {
            LOGGER.severe("The live room connection manager is unavailable.");

            closeSocket(
                    socket,
                    CloseReason.CloseCodes.UNEXPECTED_CONDITION,
                    "The live room is unavailable."
            );
            return;
        }

        hub = roomHub;

        socket.setMaxTextMessageBufferSize(MAX_FRAME_LENGTH);
        socket.setMaxBinaryMessageBufferSize(1024);
        socket.setMaxIdleTimeout(IDLE_TIMEOUT_MS);

        HttpSession httpSession =
                ChatSocketConfigurator.getHttpSession(config);

        if (!hub.register(socket, httpSession)) {
            return;
        }

        MessageHandler.Whole<String> textHandler =
                text -> handleText(socket, text);

        socket.addMessageHandler(String.class, textHandler);

        MessageHandler.Whole<ByteBuffer> binaryHandler =
                bytes -> closeSocket(
                        socket,
                        CloseReason.CloseCodes.CANNOT_ACCEPT,
                        "Send live room messages as JSON text."
                );

        socket.addMessageHandler(ByteBuffer.class, binaryHandler);

        UserRepository.Account account = hub.currentAccount(socket);

        if (account != null) {
            hub.send(socket, Map.of(
                    "type", "ready",
                    "username", account.username()
            ));
        }
    }

    private void handleText(Session socket, String text) {
        UserRepository.Account account = hub.currentAccount(socket);

        if (account == null) {
            return;
        }

        if (text == null || text.length() > MAX_FRAME_LENGTH) {
            closeSocket(
                    socket,
                    CloseReason.CloseCodes.TOO_BIG,
                    "The message frame is too large."
            );
            return;
        }

        try {
            JsonObject body = GSON.fromJson(text, JsonObject.class);

            if (body == null) {
                throw new IllegalArgumentException("Send a JSON object.");
            }

            String type = requiredString(body, "type");

            switch (type) {
                case "ping" -> hub.send(
                        socket,
                        Map.of("type", "pong")
                );

                case "message" -> {
                    String clientId = requiredString(body, "clientId");

                    if (!CLIENT_ID_PATTERN.matcher(clientId).matches()) {
                        throw new IllegalArgumentException(
                                "The message identifier is invalid."
                        );
                    }

                    String content =
                            requiredString(body, "content").trim();

                    if (content.isEmpty()
                            || content.length() > MAX_CONTENT_LENGTH) {
                        throw new IllegalArgumentException(
                                "Enter a message between 1 and 2000 characters."
                        );
                    }

                    RoomMessage message = new RoomMessage(
                            clientId,
                            account.username(),
                            content,
                            System.currentTimeMillis()
                    );

                    hub.broadcast(Map.of(
                            "type", "room_message",
                            "message", message
                    ));
                }

                default -> throw new IllegalArgumentException(
                        "Unknown live room message type."
                );
            }

        } catch (JsonParseException exception) {
            sendError(socket, "Send a valid JSON object.");

        } catch (IllegalArgumentException exception) {
            sendError(socket, exception.getMessage());
        }
    }

    private String requiredString(JsonObject body, String field) {
        JsonElement value = body.get(field);

        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(
                    "Missing or invalid field: " + field
            );
        }

        return value.getAsString();
    }

    private void sendError(Session socket, String message) {
        hub.send(socket, Map.of(
                "type", "error",
                "error", message
        ));
    }

    @Override
    public void onClose(Session socket, CloseReason reason) {
        if (hub != null) {
            hub.unregister(socket);
        }
    }

    @Override
    public void onError(Session socket, Throwable error) {
        LOGGER.log(
                Level.WARNING,
                "A live room WebSocket connection failed.",
                error
        );

        closeSocket(
                socket,
                CloseReason.CloseCodes.UNEXPECTED_CONDITION,
                "The live room connection failed. Please reconnect."
        );
    }

    private void closeSocket(
            Session socket,
            CloseReason.CloseCode code,
            String reason
    ) {
        if (hub != null) {
            hub.disconnect(socket, code, reason);
            return;
        }

        try {
            if (socket != null && socket.isOpen()) {
                socket.close(new CloseReason(code, reason));
            }
        } catch (IOException | IllegalStateException exception) {
            // The connection may already have closed.
        }
    }

    private record RoomMessage(
            String clientId,
            String sender,
            String content,
            long timestamp
    ) {
    }
}