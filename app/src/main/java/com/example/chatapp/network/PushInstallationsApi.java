package com.example.chatapp.network;

import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class PushInstallationsApi {

    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private final NetworkClient network = NetworkClient.getInstance();

    @WorkerThread
    public void register(
            ApiAccount account,
            String installationId,
            String bindingId
    ) throws IOException {
        update("register", account, installationId, bindingId);
    }

    @WorkerThread
    public void unregister(
            ApiAccount account,
            String installationId,
            String bindingId
    ) throws IOException {
        update("unregister", account, installationId, bindingId);
    }

    private void update(
            String action,
            ApiAccount account,
            String installationId,
            String bindingId
    ) throws IOException {
        if (account == null) {
            throw new IllegalArgumentException(
                    "A signed-in account is required."
            );
        }

        if (installationId == null
                || !installationId.matches("[A-Za-z0-9_-]{1,256}")
                || bindingId == null
                || !bindingId.matches("[A-Za-z0-9_-]{16,128}")) {
            throw new IllegalArgumentException(
                    "Invalid notification registration details."
            );
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("installationId", installationId);
            payload.put("bindingId", bindingId);

            Request request = new Request.Builder()
                    .url(ServerConfig.BASE_URL + "api/push/" + action)
                    .header("Accept", "application/json")
                    .post(RequestBody.create(payload.toString(), JSON))
                    .build();

            try (Response response = network.getHttpClient()
                    .newCall(request)
                    .execute()) {

                ResponseBody body = response.body();
                String text = body == null ? "" : body.string();

                if (!response.isSuccessful()) {
                    throw readError(response.code(), text);
                }

                JSONObject result = new JSONObject(text);

                if (!"ok".equals(result.optString("status"))) {
                    throw new IOException(
                            "The server did not confirm the registration update."
                    );
                }

                JSONObject user = result.getJSONObject("user");
                ApiAccount confirmed = new ApiAccount(
                        user.getInt("id"),
                        user.getString("username")
                );

                if (confirmed.getId() != account.getId()
                        || !confirmed.getUsername().equals(
                        account.getUsername())) {
                    throw new IOException(
                            "The notification response belongs to another account."
                    );
                }
            }

        } catch (JSONException | IllegalArgumentException exception) {
            throw new IOException(
                    "Could not process the notification registration.",
                    exception
            );
        }
    }

    private static ApiException readError(int statusCode, String text) {
        String message = "Notification request failed (HTTP "
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

        // The caller handles expired sessions; this request may be stale.
        return new ApiException(statusCode, message);
    }
}
