package com.example.chatapp.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class ConversationRepository {

    private final ServerDatabase database;
    private final UserRepository users;

    public record Summary(
            UserRepository.Account user,
            MessageRepository.ChatMessage lastMessage,
            long unreadCount
    ) {}

    public ConversationRepository(ServerDatabase database) {
        this.database = database;
        this.users = new UserRepository(database);
    }

    public List<Summary> getConversations(String currentUsername)
            throws SQLException {

        UserRepository.Account currentUser =
                requireUser(currentUsername);

        String username = currentUser.username();

        String sql = """
                WITH latest AS (
                    SELECT
                        CASE WHEN sender = ? THEN receiver ELSE sender END
                            AS other_username,
                        MAX(id) AS last_id
                    FROM messages
                    WHERE sender = ? OR receiver = ?
                    GROUP BY other_username
                )
                SELECT
                    u.id AS user_id,
                    u.username AS username,
                    m.id AS message_id,
                    m.sender,
                    m.receiver,
                    m.content,
                    m.timestamp,
                    m.image_name,
                    (
                        SELECT COUNT(*)
                        FROM messages incoming
                        WHERE incoming.receiver = ?
                          AND incoming.sender = u.username
                          AND incoming.id > COALESCE(
                              read_state.last_read_message_id, 0
                          )
                    ) AS unread_count
                FROM latest
                JOIN messages m ON m.id = latest.last_id
                JOIN users u ON u.username = latest.other_username
                LEFT JOIN conversation_reads read_state
                    ON read_state.username = ?
                   AND read_state.other_username = u.username
                ORDER BY m.id DESC
                """;

        List<Summary> conversations = new ArrayList<>();

        try (Connection connection = database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, username);
            statement.setString(2, username);
            statement.setString(3, username);
            statement.setString(4, username);
            statement.setString(5, username);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    UserRepository.Account otherUser =
                            new UserRepository.Account(
                                    result.getInt("user_id"),
                                    result.getString("username")
                            );

                    MessageRepository.ChatMessage lastMessage =
                            new MessageRepository.ChatMessage(
                                    result.getLong("message_id"),
                                    result.getString("sender"),
                                    result.getString("receiver"),
                                    result.getString("content"),
                                    result.getLong("timestamp"),
                                    result.getString("image_name")
                            );

                    conversations.add(new Summary(
                            otherUser,
                            lastMessage,
                            result.getLong("unread_count")
                    ));
                }
            }
        }

        return List.copyOf(conversations);
    }

    public void markRead(
            String currentUsername,
            String otherUsername,
            long throughId
    ) throws SQLException {

        if (throughId <= 0) {
            throw new IllegalArgumentException(
                    "The read position must be a positive message ID."
            );
        }

        UserRepository.Account currentUser =
                requireUser(currentUsername);

        UserRepository.Account otherUser =
                requireUser(otherUsername);

        if (currentUser.id() == otherUser.id()) {
            throw new IllegalArgumentException(
                    "Choose another user."
            );
        }

        String sql = """
                INSERT INTO conversation_reads (
                    username, other_username, last_read_message_id
                )
                SELECT ?, ?, id
                FROM messages
                WHERE id = ?
                  AND (
                      (sender = ? AND receiver = ?)
                      OR (sender = ? AND receiver = ?)
                  )
                ON CONFLICT (username, other_username)
                DO UPDATE SET last_read_message_id = MAX(
                    conversation_reads.last_read_message_id,
                    excluded.last_read_message_id
                )
                """;

        try (Connection connection = database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, currentUser.username());
            statement.setString(2, otherUser.username());
            statement.setLong(3, throughId);
            statement.setString(4, currentUser.username());
            statement.setString(5, otherUser.username());
            statement.setString(6, otherUser.username());
            statement.setString(7, currentUser.username());

            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException(
                        "The message does not belong to this conversation."
                );
            }
        }
    }

    private UserRepository.Account requireUser(String username)
            throws SQLException {

        UserRepository.Account account =
                users.findByUsername(username);

        if (account == null) {
            throw new IllegalArgumentException(
                    "The selected user account does not exist."
            );
        }

        return account;
    }
}