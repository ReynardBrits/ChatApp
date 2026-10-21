package com.example.chatapp.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.chatapp.database.User;

public final class SessionManager {

    private final SharedPreferences preferences;

    public SessionManager(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(
                        Constants.PREFERENCES_NAME,
                        Context.MODE_PRIVATE
                );
    }

    public void saveSession(User user) {
        preferences.edit()
                .putInt(Constants.CURRENT_USER_ID, user.getId())
                .putString(Constants.CURRENT_USER, user.getUsername())
                .apply();
    }

    public boolean isLoggedIn() {
        return getUserId() > 0 && !getUsername().isEmpty();
    }

    public int getUserId() {
        return preferences.getInt(Constants.CURRENT_USER_ID, -1);
    }

    public String getUsername() {
        String username = preferences.getString(
                Constants.CURRENT_USER,
                ""
        );

        return username == null ? "" : username;
    }

    public void clearSession() {
        preferences.edit()
                .remove(Constants.CURRENT_USER_ID)
                .remove(Constants.CURRENT_USER)
                .apply();
    }
}