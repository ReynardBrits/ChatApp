package com.example.chatapp.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.chatapp.utils.Constants;

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

    private static ChatDatabaseHelper instance;

    public static final ExecutorService databaseExecutor =
            Executors.newSingleThreadExecutor();

    public static synchronized ChatDatabaseHelper getInstance(
            Context context
    ) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    ChatDatabaseHelper.class,
                    Constants.DATABASE_NAME
            ).build();
        }

        return instance;
    }

    public abstract UserDao userDao();

    public abstract MessageDao messageDao();
}
