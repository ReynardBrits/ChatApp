package com.example.chatapp.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.chatapp.network.ServerConfig;
import com.example.chatapp.utils.Constants;
import com.example.chatapp.utils.SessionManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {
                User.class,
                Message.class
        },
        version = 1,
        exportSchema = false
)
public abstract class ChatDatabaseHelper extends RoomDatabase {

    private static final Map<String, ChatDatabaseHelper> INSTANCES =
            new HashMap<>();

    public static final ExecutorService databaseExecutor =
            Executors.newSingleThreadExecutor();

    public static synchronized ChatDatabaseHelper getInstance(
            Context context
    ) {
        Context applicationContext = context.getApplicationContext();

        SessionManager session = new SessionManager(applicationContext);

        if (!session.isLoggedIn()) {
            throw new IllegalStateException(
                    "Log in before opening the chat cache."
            );
        }

        String databaseName = createCacheName(
                session.getUserId(),
                session.getUsername()
        );

        ChatDatabaseHelper database = INSTANCES.get(databaseName);

        if (database == null) {
            database = Room.databaseBuilder(
                    applicationContext,
                    ChatDatabaseHelper.class,
                    databaseName
            ).build();

            INSTANCES.put(databaseName, database);
        }

        return database;
    }

    private static String createCacheName(
            int accountId,
            String username
    ) {
        String identity = ServerConfig.BASE_URL
                + "\n" + accountId
                + "\n" + username;

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));

            StringBuilder name = new StringBuilder(
                    Constants.DATABASE_NAME + "_server_"
            );

            for (byte value : hash) {
                name.append(
                        Character.forDigit((value >>> 4) & 15, 16)
                );
                name.append(
                        Character.forDigit(value & 15, 16)
                );
            }

            return name.append(".db").toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Could not create the chat cache name.",
                    exception
            );
        }
    }

    public abstract UserDao userDao();

    public abstract MessageDao messageDao();
}
