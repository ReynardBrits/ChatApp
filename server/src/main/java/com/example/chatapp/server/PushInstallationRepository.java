package com.example.chatapp.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class PushInstallationRepository {

    public record Registration(
            String installationId,
            String username,
            String bindingId
    ) {}

    private final ServerDatabase database;
    private final UserRepository users;

    public PushInstallationRepository(ServerDatabase database) {
        this.database = database;
        this.users = new UserRepository(database);
    }

    public void bind(
            String username,
            String installationId,
            String bindingId
    ) throws SQLException {
        validateIds(installationId, bindingId);
        String owner = requireUsername(username);

        String sql = """
                INSERT INTO push_installations (
                    installation_id, username, binding_id, updated_at
                )
                VALUES (?, ?, ?, ?)
                ON CONFLICT (installation_id)
                DO UPDATE SET
                    username = excluded.username,
                    binding_id = excluded.binding_id,
                    updated_at = excluded.updated_at
                """;

        try (Connection connection = database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, installationId);
            statement.setString(2, owner);
            statement.setString(3, bindingId);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    public void unbind(
            String username,
            String installationId,
            String bindingId
    ) throws SQLException {
        validateIds(installationId, bindingId);
        String owner = requireUsername(username);

        String sql = """
                DELETE FROM push_installations
                WHERE installation_id = ?
                  AND username = ?
                  AND binding_id = ?
                """;

        try (Connection connection = database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, installationId);
            statement.setString(2, owner);
            statement.setString(3, bindingId);
            statement.executeUpdate();
        }
    }

    public List<Registration> findForUser(String username)
            throws SQLException {
        String owner = requireUsername(username);

        String sql = """
                SELECT installation_id, username, binding_id
                FROM push_installations
                WHERE username = ?
                ORDER BY updated_at DESC, installation_id ASC
                """;

        List<Registration> registrations = new ArrayList<>();

        try (Connection connection = database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, owner);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    registrations.add(new Registration(
                            result.getString("installation_id"),
                            result.getString("username"),
                            result.getString("binding_id")
                    ));
                }
            }
        }

        return List.copyOf(registrations);
    }

    private String requireUsername(String username)
            throws SQLException {
        UserRepository.Account account = users.findByUsername(username);

        if (account == null) {
            throw new IllegalArgumentException(
                    "The user account does not exist."
            );
        }

        return account.username();
    }

    private static void validateIds(
            String installationId,
            String bindingId
    ) {
        if (installationId == null
                || !installationId.matches("[A-Za-z0-9_-]{1,256}")) {
            throw new IllegalArgumentException(
                    "Invalid notification installation ID."
            );
        }

        if (bindingId == null
                || !bindingId.matches("[A-Za-z0-9_-]{16,128}")) {
            throw new IllegalArgumentException(
                    "Invalid notification binding ID."
            );
        }
    }
}