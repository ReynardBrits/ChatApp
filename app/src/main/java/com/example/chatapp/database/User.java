package com.example.chatapp.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "users",
        indices = {
                @Index(value = {"username"}, unique = true)
        }
)
public class User {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    private String username = "";

    // Registration will store a password hash in this field.
    @NonNull
    private String password = "";

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @NonNull
    public String getUsername() {
        return username;
    }

    public void setUsername(@NonNull String username) {
        this.username = username;
    }

    @NonNull
    public String getPassword() {
        return password;
    }

    public void setPassword(@NonNull String password) {
        this.password = password;
    }

    @NonNull
    @Override
    public String toString() {
        return username;
    }
}
