package com.example.chatapp.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class JsonServlet extends HttpServlet {

    private static final int MAX_BODY_BYTES = 8192;

    private static final Gson GSON = new GsonBuilder()
            .setStrictness(Strictness.STRICT)
            .create();

    protected JsonObject readObject(HttpServletRequest request)
            throws IOException {

        String contentType = request.getContentType();

        if (contentType == null
                || !contentType.split(";", 2)[0]
                .trim()
                .equalsIgnoreCase("application/json")) {
            throw new IllegalArgumentException(
                    "Request content type must be application/json."
            );
        }

        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            throw new IllegalArgumentException(
                    "Request body is too large."
            );
        }

        byte[] bytes = request.getInputStream()
                .readNBytes(MAX_BODY_BYTES + 1);

        if (bytes.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException(
                    "Request body is too large."
            );
        }

        String text = new String(bytes, StandardCharsets.UTF_8);

        try {
            JsonObject body = GSON.fromJson(text, JsonObject.class);

            if (body == null) {
                throw new IllegalArgumentException(
                        "Request body must contain a JSON object."
                );
            }

            return body;

        } catch (JsonParseException exception) {
            throw new IllegalArgumentException(
                    "Request body must contain a valid JSON object."
            );
        }
    }

    protected String requiredString(JsonObject body, String field) {
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

    protected void writeJson(
            HttpServletResponse response,
            int status,
            Object body
    ) throws IOException {

        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");

        GSON.toJson(body, response.getWriter());
    }

    protected void writeError(
            HttpServletResponse response,
            int status,
            String message
    ) throws IOException {

        JsonObject body = new JsonObject();
        body.addProperty("error", message);

        writeJson(response, status, body);
    }
}