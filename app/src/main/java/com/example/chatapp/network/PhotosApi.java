package com.example.chatapp.network;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class PhotosApi {

    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;

    private final NetworkClient network =
            NetworkClient.getInstance();

    // Call this method from a background thread.
    public ApiMessage sendPhoto(String receiver, File photo)
            throws IOException {

        String recipient = receiver == null
                ? ""
                : receiver.trim().toLowerCase(Locale.ROOT);

        if (!recipient.matches("[a-z0-9_]{3,24}")) {
            throw new IllegalArgumentException(
                    "Choose a valid recipient."
            );
        }

        if (photo == null || !photo.isFile() || photo.length() == 0) {
            throw new IllegalArgumentException(
                    "The photo could not be found."
            );
        }

        if (photo.length() > MAX_PHOTO_BYTES) {
            throw new IllegalArgumentException(
                    "The photo must be smaller than 5 MB."
            );
        }

        String filename = photo.getName().toLowerCase(Locale.ROOT);
        MediaType contentType;

        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            contentType = MediaType.get("image/jpeg");
        } else if (filename.endsWith(".png")) {
            contentType = MediaType.get("image/png");
        } else {
            throw new IllegalArgumentException(
                    "Choose a JPEG or PNG photo."
            );
        }

        HttpUrl url = HttpUrl.get(
                        ServerConfig.BASE_URL + "api/photos"
                ).newBuilder()
                .addQueryParameter("receiver", recipient)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .post(RequestBody.create(photo, contentType))
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
                ApiMessage message = ApiMessage.fromJson(
                        new JSONObject(text)
                );

                if (!message.hasPhoto()) {
                    throw new IOException(
                            "The server did not confirm the photo."
                    );
                }

                return message;
            } catch (JSONException exception) {
                throw new IOException(
                        "The server returned an invalid photo response.",
                        exception
                );
            }
        }
    }

    private ApiException readError(int statusCode, String text) {
        if (statusCode == 401) {
            network.clearSession();
        }

        String message = "Photo upload failed (HTTP "
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