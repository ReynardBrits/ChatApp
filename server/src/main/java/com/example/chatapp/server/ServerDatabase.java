package com.example.chatapp.server;

import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class ServerDatabase {

    private final Path databasePath;

    public ServerDatabase(Path dataDirectory)
            throws IOException, SQLException {

        Path folder = dataDirectory.toAbsolutePath().normalize();
        Files.createDirectories(folder);

        databasePath = folder.resolve("chatapp.db");

        createTables();
    }

    public Connection openConnection() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();

        // Enforce relationships between messages and user accounts.
        config.enforceForeignKeys(true);

        // Wait briefly if another connection is writing.
        config.setBusyTimeout(5000);

        return config.createConnection("jdbc:sqlite:" + databasePath);
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    private void createTables() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {

            // Allows database reads while another connection writes.
            statement.execute("PRAGMA journal_mode=WAL");

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                        password_hash TEXT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        sender TEXT NOT NULL,
                        receiver TEXT NOT NULL,
                        content TEXT NOT NULL DEFAULT '',
                        timestamp INTEGER NOT NULL,
                        image_name TEXT,

                        FOREIGN KEY (sender)
                            REFERENCES users(username)
                            ON DELETE RESTRICT,

                        FOREIGN KEY (receiver)
                            REFERENCES users(username)
                            ON DELETE RESTRICT,

                        CHECK (sender <> receiver),
                        CHECK (length(content) <= 2000),
                        CHECK (
                            length(trim(content)) > 0
                            OR image_name IS NOT NULL
                        )
                    )
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_messages_conversation
                    ON messages(sender, receiver, timestamp, id)
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_messages_receiver
                    ON messages(receiver)
                    """);

            statement.executeUpdate("""
        CREATE TABLE IF NOT EXISTS conversation_reads (
            username TEXT NOT NULL,
            other_username TEXT NOT NULL,
            last_read_message_id INTEGER NOT NULL DEFAULT 0,

            PRIMARY KEY (username, other_username),

            FOREIGN KEY (username)
                REFERENCES users(username)
                ON DELETE RESTRICT,

            FOREIGN KEY (other_username)
                REFERENCES users(username)
                ON DELETE RESTRICT,

            CHECK (username <> other_username),
            CHECK (last_read_message_id >= 0)
        )
        """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS push_installations (
                        installation_id TEXT PRIMARY KEY NOT NULL,
                        username TEXT NOT NULL,
                        binding_id TEXT NOT NULL,
                        updated_at INTEGER NOT NULL,

                        FOREIGN KEY (username)
                            REFERENCES users(username)
                            ON DELETE RESTRICT,

                        CHECK (
                            length(installation_id) BETWEEN 1 AND 256
                        ),
                        CHECK (
                            length(binding_id) BETWEEN 16 AND 128
                        ),
                        CHECK (updated_at >= 0)
                    )
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS
                        idx_push_installations_username
                    ON push_installations(username)
                    """);
        }
    }
}