package com.example.chatapp.network;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class ConversationsApi {

    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private final NetworkClient network =
            NetworkClient.getInstance();

    // Call these methods from a background thread.

    public List<ApiConversation> getConversations(
            String currentUsername
    ) throws IOException {

        String current = normalizeUsername(currentUsername);

        Request request = new Request.Builder()
                .url(ServerConfig.BASE_URL + "api/conversations")
                .header("Accept", "application/json")
                .get()
                .build();

        try {
            JSONArray array = new JSONArray(execute(request));
            List<ApiConversation> conversations = new ArrayList<>();
            Set<Integer> userIds = new HashSet<>();

            for (int index = 0; index < array.length(); index++) {
                ApiConversation conversation = ApiConversation.fromJson(
                        array.getJSONObject(index),
                        current
                );

                if (!userIds.add(conversation.getUser().getId())) {
                    throw new IOException(
                            "The server returned a duplicate conversation."
                    );
                }

                conversations.add(conversation);
            }

            return Collections.unmodifiableList(conversations);
        } catch (JSONException exception) {
            throw new IOException(
                    "The server returned an invalid conversation list.",
                    exception
            );
        }
    }

    public void markRead(String otherUsername, long throughId)
            throws IOException {

        String recipient = normalizeUsername(otherUsername);

        if (throughId <= 0) {
            throw new IllegalArgumentException(
                    "Choose a valid message to mark as read."
            );
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("with", recipient);
            payload.put("throughId", throughId);

            Request request = new Request.Builder()
                    .url(ServerConfig.BASE_URL + "api/conversations/read")
                    .header("Accept", "application/json")
                    .post(RequestBody.create(payload.toString(), JSON))
                    .build();

            JSONObject result = new JSONObject(execute(request));

            if (!"ok".equals(result.optString("status"))) {
                throw new IOException(
                        "The server did not confirm the read update."
                );
            }
        } catch (JSONException exception) {
            throw new IOException(
                    "Could not process the conversation read update.",
                    exception
            );
        }
    }

    private String execute(Request request) throws IOException {
        try (Response response = network.getHttpClient()
                .newCall(request)
                .execute()) {

            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();

            if (!response.isSuccessful()) {
                throw readError(response.code(), text);
            }

            return text;
        }
    }

    private ApiException readError(int statusCode, String text) {
        if (statusCode == 401) {
            network.clearSession();
        }

        String message = "Conversation request failed (HTTP "
                + statusCode + ").";

        try {
            Object error = new JSONObject(text).opt("error");

            if (error instanceof String
                    && !((String) error).trim().isEmpty()) {
                message = (String) error;
            }
        } catch (JSONException ignored) {
            // Keep the fallback message for non-JSON errors.
        }

        return new ApiException(statusCode, message);
    }

    private static String normalizeUsername(String username) {
        String normalized = username == null
                ? ""
                : username.trim().toLowerCase(Locale.ROOT);

        if (!normalized.matches("[a-z0-9_]{3,24}")) {
            throw new IllegalArgumentException(
                    "Use a valid username."
            );
        }

        return normalized;
    }
}
