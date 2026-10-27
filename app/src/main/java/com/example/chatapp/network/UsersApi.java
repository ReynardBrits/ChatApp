package com.example.chatapp.network;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class UsersApi {

    private final NetworkClient network =
            NetworkClient.getInstance();

    // Call this method from a background thread.
    public List<ApiAccount> getOtherUsers() throws IOException {
        Request request = new Request.Builder()
                .url(ServerConfig.BASE_URL + "api/users")
                .header("Accept", "application/json")
                .get()
                .build();

        try (Response response = network.getHttpClient()
                .newCall(request)
                .execute()) {

            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();

            if (!response.isSuccessful()) {
                throw readError(response.code(), text);
            }

            try {
                JSONArray array = new JSONArray(text);
                List<ApiAccount> users = new ArrayList<>();

                for (int index = 0; index < array.length(); index++) {
                    JSONObject account = array.getJSONObject(index);

                    users.add(
                            new ApiAccount(
                                    account.getInt("id"),
                                    account.getString("username")
                            )
                    );
                }

                return Collections.unmodifiableList(users);
            } catch (JSONException | IllegalArgumentException exception) {
                throw new IOException(
                        "The server returned an invalid user list.",
                        exception
                );
            }
        }
    }

    private ApiException readError(int statusCode, String text) {
        if (statusCode == 401) {
            network.clearSession();
        }

        String message = "Could not load users (HTTP "
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
}