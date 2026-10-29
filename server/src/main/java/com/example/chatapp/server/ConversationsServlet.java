package com.example.chatapp.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

public final class ConversationsServlet extends JsonServlet {

    private static final long serialVersionUID = 1L;

    private ConversationRepository conversations;

    @Override
    public void init() throws ServletException {
        super.init();

        Object value = getServletContext().getAttribute(
                ServerDatabase.class.getName()
        );

        if (!(value instanceof ServerDatabase database)) {
            throw new ServletException(
                    "The server database has not been initialized."
            );
        }

        conversations = new ConversationRepository(database);
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        UserRepository.Account account =
                signedInAccount(request, response);

        if (account == null) {
            return;
        }

        String path = request.getPathInfo();

        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            writeError(
                    response,
                    HttpServletResponse.SC_NOT_FOUND,
                    "Unknown conversation route."
            );
            return;
        }

        try {
            writeJson(
                    response,
                    HttpServletResponse.SC_OK,
                    conversations.getConversations(account.username())
            );

        } catch (IllegalArgumentException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    exception.getMessage()
            );

        } catch (SQLException exception) {
            getServletContext().log(
                    "Unable to load conversation summaries.",
                    exception
            );

            writeError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load conversations. Please try again."
            );
        }
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        UserRepository.Account account =
                signedInAccount(request, response);

        if (account == null) {
            return;
        }

        if (!"/read".equals(request.getPathInfo())) {
            writeError(
                    response,
                    HttpServletResponse.SC_NOT_FOUND,
                    "Unknown conversation route."
            );
            return;
        }

        try {
            JsonObject body = readObject(request);

            conversations.markRead(
                    account.username(),
                    requiredString(body, "with"),
                    requiredPositiveLong(body, "throughId")
            );

            writeJson(
                    response,
                    HttpServletResponse.SC_OK,
                    Map.of("status", "ok")
            );

        } catch (IllegalArgumentException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    exception.getMessage()
            );

        } catch (SQLException exception) {
            getServletContext().log(
                    "Unable to save the conversation read position.",
                    exception
            );

            writeError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to mark the conversation as read. Please try again."
            );
        }
    }

    private UserRepository.Account signedInAccount(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        UserRepository.Account account =
                AuthServlet.currentAccount(request);

        if (account == null) {
            writeError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Please log in."
            );
        }

        return account;
    }

    private static long requiredPositiveLong(
            JsonObject body,
            String field
    ) {
        JsonElement value = body.get(field);

        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(
                    field + " must be a positive integer."
            );
        }

        String digits = value.getAsString();

        if (!digits.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException(
                    field + " must be a positive integer."
            );
        }

        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    field + " must be a positive integer."
            );
        }
    }
}