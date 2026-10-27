package com.example.chatapp.network;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class AuthApi {

    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private final NetworkClient network =
            NetworkClient.getInstance();

    // Call these methods from a background thread.

    public ApiAccount register(String username, String password)
            throws IOException {

        JSONObject result = post(
                "register",
                credentials(username, password)
        );

        return readAccount(result);
    }

    public ApiAccount login(String username, String password)
            throws IOException {

        JSONObject payload = credentials(username, password);

        try {
            return readAccount(post("login", payload));
        } catch (IOException exception) {
            network.clearSession();
            throw exception;
        }
    }

    public ApiAccount getCurrentAccount() throws IOException {
        Request request = new Request.Builder()
                .url(ServerConfig.BASE_URL + "api/auth/me")
                .header("Accept", "application/json")
                .get()
                .build();

        return readAccount(execute(request));
    }

    public void logout() throws IOException {
        try {
            post("logout", new JSONObject());
        } finally {
            network.clearSession();
        }
    }

    private JSONObject post(String action, JSONObject payload)
            throws IOException {

        RequestBody body = RequestBody.create(
                payload.toString(),
                JSON
        );

        Request request = new Request.Builder()
                .url(ServerConfig.BASE_URL + "api/auth/" + action)
                .header("Accept", "application/json")
                .post(body)
                .build();

        return execute(request);
    }

    private JSONObject execute(Request request) throws IOException {
        try (Response response = network.getHttpClient()
                .newCall(request)
                .execute()) {

            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();

            if (!response.isSuccessful()) {
                if (response.code() == 401) {
                    network.clearSession();
                }

                String message = "Server request failed (HTTP "
                        + response.code() + ").";

                try {
                    Object error = new JSONObject(text).opt("error");

                    if (error instanceof String
                            && !((String) error).trim().isEmpty()) {
                        message = (String) error;
                    }
                } catch (JSONException ignored) {
                    // Keep the fallback message for non-JSON errors.
                }

                throw new ApiException(response.code(), message);
            }

            try {
                return new JSONObject(text);
            } catch (JSONException exception) {
                throw new IOException(
                        "The server returned an invalid response.",
                        exception
                );
            }
        }
    }

    private static JSONObject credentials(
            String username,
            String password
    ) throws IOException {

        if (username == null || password == null) {
            throw new IllegalArgumentException(
                    "Username and password are required."
            );
        }

        try {
            JSONObject payload = new JSONObject();

            payload.put(
                    "username",
                    username.trim().toLowerCase(Locale.ROOT)
            );
            payload.put("password", password);

            return payload;
        } catch (JSONException exception) {
            throw new IOException(
                    "Could not prepare the account request.",
                    exception
            );
        }
    }

    private static ApiAccount readAccount(JSONObject json)
            throws IOException {

        try {
            return new ApiAccount(
                    json.getInt("id"),
                    json.getString("username")
            );
        } catch (JSONException | IllegalArgumentException exception) {
            throw new IOException(
                    "The server returned invalid account details.",
                    exception
            );
        }
    }
}