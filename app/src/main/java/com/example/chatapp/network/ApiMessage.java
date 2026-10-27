package com.example.chatapp.network;

import androidx.annotation.Nullable;

import com.example.chatapp.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public final class ApiMessage {

    private static final String USERNAME_PATTERN =
            "[a-z0-9_]{3,24}";

    private static final String PHOTO_NAME_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
                    + "[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)";

    private final long id;
    private final String sender;
    private final String receiver;
    private final String content;
    private final long timestamp;

    @Nullable
    private final String imageName;

    private ApiMessage(
            long id,
            String sender,
            String receiver,
            String content,
            long timestamp,
            @Nullable String imageName
    ) {
        if (id <= 0 || timestamp <= 0) {
            throw new IllegalArgumentException(
                    "Invalid message ID or timestamp."
            );
        }

        if (sender == null || !sender.matches(USERNAME_PATTERN)
                || receiver == null
                || !receiver.matches(USERNAME_PATTERN)
                || sender.equals(receiver)) {
            throw new IllegalArgumentException(
                    "Invalid message participants."
            );
        }

        if (imageName != null
                && !imageName.matches(PHOTO_NAME_PATTERN)) {
            throw new IllegalArgumentException(
                    "Invalid photo filename."
            );
        }

        if (content == null
                || content.length() > Constants.MAX_MESSAGE_LENGTH
                || (content.trim().isEmpty() && imageName == null)) {
            throw new IllegalArgumentException(
                    "Invalid message content."
            );
        }

        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.timestamp = timestamp;
        this.imageName = imageName;
    }

    public static ApiMessage fromJson(JSONObject json)
            throws IOException {

        if (json == null) {
            throw new IOException(
                    "The server returned an empty message."
            );
        }

        try {
            String imageName = json.isNull("imageName")
                    ? null
                    : json.getString("imageName");

            return new ApiMessage(
                    json.getLong("id"),
                    json.getString("sender"),
                    json.getString("receiver"),
                    json.getString("content"),
                    json.getLong("timestamp"),
                    imageName
            );
        } catch (JSONException | IllegalArgumentException exception) {
            throw new IOException(
                    "The server returned invalid message details.",
                    exception
            );
        }
    }

    public long getId() {
        return id;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Nullable
    public String getImageName() {
        return imageName;
    }

    public boolean hasPhoto() {
        return imageName != null;
    }
}