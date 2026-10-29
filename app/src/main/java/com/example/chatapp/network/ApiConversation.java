package com.example.chatapp.network;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public final class ApiConversation {

    private final ApiAccount user;
    private final ApiMessage lastMessage;
    private final long unreadCount;

    private ApiConversation(
            ApiAccount user,
            ApiMessage lastMessage,
            long unreadCount,
            String currentUsername
    ) {
        if (user == null || lastMessage == null) {
            throw new IllegalArgumentException(
                    "A conversation needs a user and a message."
            );
        }

        if (currentUsername == null
                || !currentUsername.matches("[a-z0-9_]{3,24}")
                || currentUsername.equals(user.getUsername())) {
            throw new IllegalArgumentException(
                    "Invalid conversation account."
            );
        }

        String otherUsername = user.getUsername();

        boolean sentByCurrentUser =
                currentUsername.equals(lastMessage.getSender())
                        && otherUsername.equals(lastMessage.getReceiver());

        boolean receivedByCurrentUser =
                otherUsername.equals(lastMessage.getSender())
                        && currentUsername.equals(lastMessage.getReceiver());

        if (!sentByCurrentUser && !receivedByCurrentUser) {
            throw new IllegalArgumentException(
                    "The latest message belongs to another conversation."
            );
        }

        if (unreadCount < 0) {
            throw new IllegalArgumentException(
                    "The unread count cannot be negative."
            );
        }

        this.user = user;
        this.lastMessage = lastMessage;
        this.unreadCount = unreadCount;
    }

    public static ApiConversation fromJson(
            JSONObject json,
            String currentUsername
    ) throws IOException {
        if (json == null) {
            throw new IOException(
                    "The server returned an empty conversation."
            );
        }

        try {
            JSONObject accountJson = json.getJSONObject("user");

            ApiAccount otherUser = new ApiAccount(
                    accountJson.getInt("id"),
                    accountJson.getString("username")
            );

            ApiMessage latestMessage = ApiMessage.fromJson(
                    json.getJSONObject("lastMessage")
            );

            return new ApiConversation(
                    otherUser,
                    latestMessage,
                    json.getLong("unreadCount"),
                    currentUsername
            );

        } catch (JSONException | IllegalArgumentException exception) {
            throw new IOException(
                    "The server returned invalid conversation details.",
                    exception
            );
        }
    }

    public ApiAccount getUser() {
        return user;
    }

    public ApiMessage getLastMessage() {
        return lastMessage;
    }

    public long getUnreadCount() {
        return unreadCount;
    }

    public boolean hasUnreadMessages() {
        return unreadCount > 0;
    }
}