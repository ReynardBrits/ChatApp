package com.example.chatapp.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class PhotoMessageRepository {

    private final ServerDatabase database;
    private final UserRepository users;

    public PhotoMessageRepository(ServerDatabase database) {
        this.database = database;
        this.users = new UserRepository(database);
    }

    public MessageRepository.ChatMessage sendPhoto(
            String sender,
            String receiver,
            String imageName
    ) throws SQLException {

        UserRepository.Account senderAccount =
                users.findByUsername(sender);

        UserRepository.Account receiverAccount =
                users.findByUsername(receiver);

        if (senderAccount == null || receiverAccount == null) {
            throw new IllegalArgumentException(
                    "The selected user account does not exist."
            );
        }

        if (senderAccount.id() == receiverAccount.id()) {
            throw new IllegalArgumentException(
                    "Choose another user to share a photo."
            );
        }

        if (imageName == null || !imageName.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
                        + "[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)"
        )) {
            throw new IllegalArgumentException(
                    "Invalid photo filename."
            );
        }

        long timestamp = System.currentTimeMillis();

        String sql = """
                INSERT INTO messages (
                    sender, receiver, content, timestamp, image_name
                )
                VALUES (?, ?, '', ?, ?)
                """;

        try (Connection connection = database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, senderAccount.username());
            statement.setString(2, receiverAccount.username());
            statement.setLong(3, timestamp);
            statement.setString(4, imageName);
            statement.executeUpdate();

            try (Statement query = connection.createStatement();
                 ResultSet result = query.executeQuery(
                         "SELECT last_insert_rowid()"
                 )) {

                if (!result.next()) {
                    throw new SQLException(
                            "Unable to retrieve the saved photo message ID."
                    );
                }

                return new MessageRepository.ChatMessage(
                        result.getLong(1),
                        senderAccount.username(),
                        receiverAccount.username(),
                        "",
                        timestamp,
                        imageName
                );
            }
        }
    }

    public boolean canViewPhoto(
            String username,
            String imageName
    ) throws SQLException {

        String sql = """
                SELECT 1
                FROM messages
                WHERE image_name = ?
                  AND (sender = ? OR receiver = ?)
                LIMIT 1
                """;

        try (Connection connection = database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, imageName);
            statement.setString(2, username);
            statement.setString(3, username);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}