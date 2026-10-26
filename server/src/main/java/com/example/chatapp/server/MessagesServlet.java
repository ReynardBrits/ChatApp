package com.example.chatapp.server;

import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public final class MessagesServlet extends JsonServlet {

    private MessageRepository messages;
    private ChatHub hub;

    @Override
    public void init() throws ServletException {
        super.init();

        Object databaseValue = getServletContext().getAttribute(
                ServerDatabase.class.getName()
        );

        Object hubValue = getServletContext().getAttribute(
                ChatHub.class.getName()
        );

        if (!(databaseValue instanceof ServerDatabase database)
                || !(hubValue instanceof ChatHub chatHub)) {

            throw new ServletException(
                    "The chat database and connection manager must be initialized."
            );
        }

        messages = new MessageRepository(database);
        hub = chatHub;
    }

    @Override
    protected void doPost(
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
            return;
        }

        try {
            JsonObject body = readObject(request);

            String receiver = requiredString(body, "receiver");
            String content = requiredString(body, "content");

            MessageRepository.ChatMessage message = messages.sendText(
                    account.username(),
                    receiver,
                    content
            );

            hub.publish(message);

            writeJson(
                    response,
                    HttpServletResponse.SC_CREATED,
                    message
            );

        } catch (IllegalArgumentException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    exception.getMessage()
            );

        } catch (SQLException exception) {
            getServletContext().log(
                    "Unable to save the message.",
                    exception
            );

            writeError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to send the message. Please try again."
            );
        }
    }

    @Override
    protected void doGet(
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
            return;
        }

        try {
            String otherUsername = request.getParameter("with");

            if (otherUsername == null || otherUsername.isBlank()) {
                throw new IllegalArgumentException(
                        "Specify the other username using the with parameter."
                );
            }

            String beforeValue = request.getParameter("beforeId");
            String limitValue = request.getParameter("limit");

            long beforeId = beforeValue == null
                    ? 0
                    : Long.parseLong(beforeValue);

            int limit = limitValue == null
                    ? 50
                    : Integer.parseInt(limitValue);

            List<MessageRepository.ChatMessage> conversation =
                    messages.getConversation(
                            account.username(),
                            otherUsername,
                            beforeId,
                            limit
                    );

            writeJson(
                    response,
                    HttpServletResponse.SC_OK,
                    conversation
            );

        } catch (NumberFormatException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "beforeId and limit must be whole numbers."
            );

        } catch (IllegalArgumentException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    exception.getMessage()
            );

        } catch (SQLException exception) {
            getServletContext().log(
                    "Unable to load the conversation.",
                    exception
            );

            writeError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load messages. Please try again."
            );
        }
    }
}