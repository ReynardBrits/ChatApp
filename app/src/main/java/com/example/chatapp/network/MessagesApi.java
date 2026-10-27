package com.example.chatapp.network;

import com.example.chatapp.utils.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class MessagesApi {

    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private final NetworkClient network =
            NetworkClient.getInstance();

    // Call these methods from a background thread.

    public ApiMessage sendText(String receiver, String content)
            throws IOException {

        String recipient = normalizeUsername(receiver);
        String text = content == null ? "" : content.trim();

        if (text.isEmpty()
                || text.length() > Constants.MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException(
                    "Messages must contain between 1 and 2,000 characters."
            );
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("receiver", recipient);
            payload.put("content", text);

            Request request = new Request.Builder()
                    .url(ServerConfig.BASE_URL + "api/messages")
                    .header("Accept", "application/json")
                    .post(RequestBody.create(payload.toString(), JSON))
                    .build();

            return ApiMessage.fromJson(
                    new JSONObject(execute(request))
            );
        } catch (JSONException exception) {
            throw new IOException(
                    "Could not process the message request.",
                    exception
            );
        }
    }

    public List<ApiMessage> getConversation(String otherUsername)
            throws IOException {

        return getConversation(otherUsername, 0, 50);
    }

    public List<ApiMessage> getConversation(
            String otherUsername,
            long beforeId,
            int limit
    ) throws IOException {

        String recipient = normalizeUsername(otherUsername);

        if (beforeId < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "Invalid conversation page."
            );
        }

        HttpUrl url = HttpUrl.get(
                        ServerConfig.BASE_URL + "api/messages"
                ).newBuilder()
                .addQueryParameter("with", recipient)
                .addQueryParameter("beforeId", Long.toString(beforeId))
                .addQueryParameter("limit", Integer.toString(limit))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build();

        try {
            JSONArray array = new JSONArray(execute(request));
            List<ApiMessage> messages = new ArrayList<>();

            for (int index = 0; index < array.length(); index++) {
                messages.add(
                        ApiMessage.fromJson(
                                array.getJSONObject(index)
                        )
                );
            }

            return Collections.unmodifiableList(messages);
        } catch (JSONException exception) {
            throw new IOException(
                    "The server returned an invalid conversation.",
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

        String message = "Message request failed (HTTP "
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
                    "Choose a valid recipient."
            );
        }

        return normalized;
    }
}