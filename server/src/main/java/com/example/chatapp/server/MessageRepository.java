package com.example.chatapp.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MessageRepository {

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_PAGE_SIZE = 100;

    private final ServerDatabase database;
    private final UserRepository users;

    public record ChatMessage(
            long id,
            String sender,
            String receiver,
            String content,
            long timestamp,
            String imageName
    ) {}

    public MessageRepository(ServerDatabase database) {
        this.database = database;
        this.users = new UserRepository(database);
    }

    public ChatMessage sendText(
            String sender,
            String receiver,
            String content
    ) throws SQLException {

        UserRepository.Account senderAccount = requireUser(sender);
        UserRepository.Account receiverAccount = requireUser(receiver);

        checkDifferentUsers(senderAccount, receiverAccount);

        String messageText = content == null ? "" : content.trim();

        if (messageText.isEmpty()) {
            throw new IllegalArgumentException("Enter a message.");
        }

        if (messageText.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException(
                    "Messages cannot exceed 2000 characters."
            );
        }

        long timestamp = System.currentTimeMillis();

        String sql = """
                INSERT INTO messages (sender, receiver, content, timestamp)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, senderAccount.username());
            statement.setString(2, receiverAccount.username());
            statement.setString(3, messageText);
            statement.setLong(4, timestamp);
            statement.executeUpdate();

            try (Statement query = connection.createStatement();
                 ResultSet result = query.executeQuery(
                         "SELECT last_insert_rowid()"
                 )) {

                if (!result.next()) {
                    throw new SQLException(
                            "Unable to retrieve the saved message ID."
                    );
                }

                return new ChatMessage(
                        result.getLong(1),
                        senderAccount.username(),
                        receiverAccount.username(),
                        messageText,
                        timestamp,
                        null
                );
            }
        }
    }

    public List<ChatMessage> getConversation(
            String currentUsername,
            String otherUsername,
            long beforeId,
            int limit
    ) throws SQLException {

        if (beforeId < 0) {
            throw new IllegalArgumentException(
                    "The message cursor cannot be negative."
            );
        }

        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "The message limit must be between 1 and 100."
            );
        }

        UserRepository.Account currentUser = requireUser(currentUsername);
        UserRepository.Account otherUser = requireUser(otherUsername);

        checkDifferentUsers(currentUser, otherUser);

        String sql = """
                SELECT id, sender, receiver, content, timestamp, image_name
                FROM messages
                WHERE (
                    (sender = ? AND receiver = ?)
                    OR (sender = ? AND receiver = ?)
                )
                AND (? = 0 OR id < ?)
                ORDER BY id DESC
                LIMIT ?
                """;

        List<ChatMessage> messages = new ArrayList<>();

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, currentUser.username());
            statement.setString(2, otherUser.username());
            statement.setString(3, otherUser.username());
            statement.setString(4, currentUser.username());
            statement.setLong(5, beforeId);
            statement.setLong(6, beforeId);
            statement.setInt(7, limit);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    messages.add(new ChatMessage(
                            result.getLong("id"),
                            result.getString("sender"),
                            result.getString("receiver"),
                            result.getString("content"),
                            result.getLong("timestamp"),
                            result.getString("image_name")
                    ));
                }
            }
        }

        Collections.reverse(messages);
        return messages;
    }

    private UserRepository.Account requireUser(String username)
            throws SQLException {

        UserRepository.Account account = users.findByUsername(username);

        if (account == null) {
            throw new IllegalArgumentException(
                    "The selected user account does not exist."
            );
        }

        return account;
    }

    private void checkDifferentUsers(
            UserRepository.Account first,
            UserRepository.Account second
    ) {
        if (first.id() == second.id()) {
            throw new IllegalArgumentException(
                    "Choose another user to start a conversation."
            );
        }
    }
}