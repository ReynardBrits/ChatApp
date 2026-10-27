package com.example.chatapp.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.chatapp.database.User;
import com.example.chatapp.network.ApiAccount;
import com.example.chatapp.network.NetworkClient;
import com.example.chatapp.network.ServerConfig;

import okhttp3.HttpUrl;

public final class SessionManager {

    private static final String CURRENT_SERVER = "current_server_url";

    private final SharedPreferences preferences;

    public SessionManager(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(
                        Constants.PREFERENCES_NAME,
                        Context.MODE_PRIVATE
                );
    }

    public void saveSession(ApiAccount account) {
        preferences.edit()
                .putInt(Constants.CURRENT_USER_ID, account.getId())
                .putString(
                        Constants.CURRENT_USER,
                        account.getUsername()
                )
                .putString(CURRENT_SERVER, ServerConfig.BASE_URL)
                .apply();
    }

    // Retained while we replace the old local login code.
    @Deprecated
    public void saveSession(User user) {
        preferences.edit()
                .putInt(Constants.CURRENT_USER_ID, user.getId())
                .putString(Constants.CURRENT_USER, user.getUsername())
                .remove(CURRENT_SERVER)
                .apply();
    }

    public boolean isLoggedIn() {
        String savedServer = preferences.getString(
                CURRENT_SERVER,
                ""
        );

        if (!ServerConfig.BASE_URL.equals(savedServer)
                || getUserId() <= 0
                || getUsername().isEmpty()) {
            return false;
        }

        HttpUrl accountUrl = HttpUrl.get(
                ServerConfig.BASE_URL + "api/auth/me"
        );

        return !NetworkClient.getInstance()
                .getHttpClient()
                .cookieJar()
                .loadForRequest(accountUrl)
                .isEmpty();
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
        NetworkClient.getInstance().clearSession();

        preferences.edit()
                .remove(Constants.CURRENT_USER_ID)
                .remove(Constants.CURRENT_USER)
                .remove(CURRENT_SERVER)
                .apply();
    }
}