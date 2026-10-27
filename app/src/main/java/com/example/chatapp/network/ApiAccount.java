package com.example.chatapp.network;

public final class ApiAccount {

    private final int id;
    private final String username;

    public ApiAccount(int id, String username) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "Account ID must be positive."
            );
        }

        if (username == null
                || !username.matches("[a-z0-9_]{3,24}")) {

            throw new IllegalArgumentException(
                    "Invalid account username."
            );
        }

        this.id = id;
        this.username = username;
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public String toString() {
        return username;
    }
}