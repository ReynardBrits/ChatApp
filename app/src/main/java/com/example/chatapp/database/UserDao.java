package com.example.chatapp.database;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface UserDao {

    @Insert
    long insertUser(User user);

    @Upsert
    void upsertUser(User user);

    @Upsert
    void upsertUsers(List<User> users);

    @Nullable
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    User getUserByUsername(String username);

    @Query(
            "SELECT * FROM users "
                    + "WHERE id != :currentUserId "
                    + "ORDER BY username ASC"
    )
    LiveData<List<User>> getOtherUsers(int currentUserId);
}
