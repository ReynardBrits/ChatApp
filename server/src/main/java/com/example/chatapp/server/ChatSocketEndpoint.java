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
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ChatSocketEndpoint extends Endpoint {

    private static final int MAX_FRAME_LENGTH = 16384;
    private static final long IDLE_TIMEOUT_MS = 5 * 60 * 1000L;

    private static final Logger LOGGER =
            Logger.getLogger(ChatSocketEndpoint.class.getName());

    private static final Gson GSON = new GsonBuilder()
            .setStrictness(Strictness.STRICT)
            .create();

    private ChatHub hub;
    private MessageRepository messages;

    @Override
    public void onOpen(Session socket, EndpointConfig config) {
        Object hubValue = config.getUserProperties().get(
                ChatHub.class.getName()
        );

        Object databaseValue = config.getUserProperties().get(
                ServerDatabase.class.getName()
        );

        if (!(hubValue instanceof ChatHub chatHub)
                || !(databaseValue instanceof ServerDatabase database)) {

            LOGGER.severe(
                    "WebSocket dependencies have not been initialized."
            );

            closeSocket(
                    socket,
                    CloseReason.CloseCodes.UNEXPECTED_CONDITION,
                    "The chat service is unavailable."
            );
            return;
        }

        hub = chatHub;
        messages = new MessageRepository(database);

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
                        "Send text messages as JSON."
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
                throw new IllegalArgumentException(
                        "Send a JSON object."
                );
            }

            String type = requiredString(body, "type");

            switch (type) {
                case "ping" -> hub.send(
                        socket,
                        Map.of("type", "pong")
                );

                case "message" -> {
                    String receiver = requiredString(body, "receiver");
                    String content = requiredString(body, "content");

                    MessageRepository.ChatMessage saved =
                            messages.sendText(
                                    account.username(),
                                    receiver,
                                    content
                            );

                    hub.publish(saved);
                }

                default -> throw new IllegalArgumentException(
                        "Unknown WebSocket message type."
                );
            }

        } catch (JsonParseException exception) {
            sendError(socket, "Send a valid JSON object.");

        } catch (IllegalArgumentException exception) {
            sendError(socket, exception.getMessage());

        } catch (SQLException exception) {
            LOGGER.log(
                    Level.WARNING,
                    "Unable to save a WebSocket message.",
                    exception
            );

            sendError(
                    socket,
                    "Unable to send the message. Please try again."
            );
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
                "A WebSocket connection failed.",
                error
        );

        closeSocket(
                socket,
                CloseReason.CloseCodes.UNEXPECTED_CONDITION,
                "The connection failed. Please reconnect."
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
}