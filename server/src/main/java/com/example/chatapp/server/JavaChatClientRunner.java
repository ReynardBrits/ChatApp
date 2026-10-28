package com.example.chatapp.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;

import javax.websocket.DeploymentException;

import java.io.Console;
import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

public final class JavaChatClientRunner {

    private static final URI BASE_URI =
            URI.create("http://127.0.0.1:8081/");

    private static final Gson GSON = new GsonBuilder()
            .setStrictness(Strictness.STRICT)
            .create();

    private JavaChatClientRunner() {}

    public static void main(String[] args) {
        Console console = System.console();

        if (console == null) {
            System.err.println(
                    "No interactive console is available. "
                            + "Run this client from a PowerShell terminal."
            );
            return;
        }

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        String sessionCookie = null;

        try {
            String enteredUsername = console.readLine("Username: ");

            if (enteredUsername == null) {
                return;
            }

            String username = normalizeUsername(enteredUsername);

            char[] password = console.readPassword("Password: ");

            if (password == null) {
                return;
            }

            LoginResult login;

            try {
                login = login(http, username, password);
            } finally {
                Arrays.fill(password, '\0');
            }

            sessionCookie = login.sessionCookie();

            String enteredReceiver =
                    console.readLine("Recipient username: ");

            if (enteredReceiver == null) {
                return;
            }

            String receiver = normalizeUsername(enteredReceiver);

            if (receiver.equals(login.username())) {
                throw new IllegalArgumentException(
                        "Choose another user as the recipient."
                );
            }

            try (JavaChatClient client = new JavaChatClient(
                    login.username(),
                    JavaChatClientRunner::displayEvent
            )) {
                client.connect(sessionCookie);

                System.out.println("Chatting with " + receiver + ".");
                System.out.println(
                        "Type a message and press Enter. "
                                + "Type /quit to leave."
                );

                while (client.isConnected()) {
                    String line = console.readLine("> ");

                    if (line == null
                            || "/quit".equalsIgnoreCase(line.trim())) {
                        break;
                    }

                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    try {
                        client.sendText(receiver, line);

                    } catch (IllegalArgumentException exception) {
                        System.err.println(exception.getMessage());

                    } catch (IOException exception) {
                        System.err.println(
                                "Could not confirm sending. Check the "
                                        + "conversation before retrying."
                        );
                        break;
                    }
                }
            }

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("The client was interrupted.");

        } catch (IOException | DeploymentException
                 | IllegalArgumentException exception) {
            System.err.println(
                    "Client stopped: "
                            + safe(String.valueOf(exception.getMessage()))
            );

        } finally {
            if (sessionCookie != null) {
                logout(http, sessionCookie);
            }
        }
    }

    private static LoginResult login(
            HttpClient http,
            String username,
            char[] password
    ) throws IOException, InterruptedException {

        String body = GSON.toJson(Map.of(
                "username", username,
                "password", new String(password)
        ));

        HttpRequest request = HttpRequest.newBuilder(
                        BASE_URI.resolve("api/auth/login")
                )
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        body,
                        StandardCharsets.UTF_8
                ))
                .build();

        HttpResponse<String> response = http.send(
                request,
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8
                )
        );

        if (response.statusCode() == 401) {
            throw new IOException("Invalid username or password.");
        }

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Login failed with HTTP " + response.statusCode() + "."
            );
        }

        try {
            JsonObject account = GSON.fromJson(
                    response.body(),
                    JsonObject.class
            );

            if (account == null
                    || !username.equals(
                    account.get("username").getAsString()
            )
                    || account.get("id").getAsInt() <= 0) {
                throw new IOException(
                        "The server returned an unexpected account."
                );
            }

            String cookie = response.headers()
                    .allValues("Set-Cookie")
                    .stream()
                    .flatMap(header -> HttpCookie.parse(header).stream())
                    .filter(value ->
                            "CHATAPPSESSION".equals(value.getName())
                                    && !value.hasExpired()
                    )
                    .map(value ->
                            value.getName() + "=" + value.getValue()
                    )
                    .filter(value -> value.matches(
                            "CHATAPPSESSION=[A-Za-z0-9._~-]{1,256}"
                    ))
                    .findFirst()
                    .orElseThrow(() -> new IOException(
                            "The server did not provide a login session."
                    ));

            return new LoginResult(username, cookie);

        } catch (RuntimeException exception) {
            throw new IOException(
                    "The server returned an invalid login response.",
                    exception
            );
        }
    }

    private static void displayEvent(JsonObject event) {
        String type = event.get("type").getAsString();

        switch (type) {
            case "ready" -> System.out.println(
                    "Connected as "
                            + safe(event.get("username").getAsString())
                            + "."
            );

            case "message" -> {
                JsonObject message = event.getAsJsonObject("message");

                String content = safe(
                        message.get("content").getAsString()
                );

                boolean hasPhoto = message.has("imageName")
                        && !message.get("imageName").isJsonNull();

                if (hasPhoto) {
                    content = content.isEmpty()
                            ? "[Photo]"
                            : content + " [Photo]";
                }

                System.out.println(
                        "\n"
                                + safe(message.get("sender").getAsString())
                                + " -> "
                                + safe(message.get("receiver").getAsString())
                                + ": " + content
                );
            }

            case "error" -> System.err.println(
                    safe(event.get("error").getAsString())
            );

            default -> {
                // Other connection events need no console output.
            }
        }
    }

    private static void logout(
            HttpClient http,
            String sessionCookie
    ) {
        HttpRequest request = HttpRequest.newBuilder(
                        BASE_URI.resolve("api/auth/logout")
                )
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Cookie", sessionCookie)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        try {
            HttpResponse<Void> response = http.send(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            );

            if (response.statusCode() != 200) {
                System.err.println("Logout could not be confirmed.");
            }

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

        } catch (IOException exception) {
            System.err.println("Logout could not be confirmed.");
        }
    }

    private static String normalizeUsername(String value) {
        String username = value.trim().toLowerCase(Locale.ROOT);

        if (!username.matches("[a-z0-9_]{3,24}")) {
            throw new IllegalArgumentException(
                    "Usernames need 3 to 24 letters, numbers, or underscores."
            );
        }

        return username;
    }

    private static String safe(String value) {
        return value.replaceAll("\\p{Cntrl}", " ");
    }

    private record LoginResult(
            String username,
            String sessionCookie
    ) {}
}