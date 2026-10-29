package com.example.chatapp.notifications;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public final class ChatMessagingService extends FirebaseMessagingService {

    private static final String PREFS = "chat_push_registration";
    private static final String KEY_INSTALLATION_ID = "installation_id";

    public static String getInstallationId(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_INSTALLATION_ID, "");
    }

    @Override
    public void onRegistered(@NonNull String installationId) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_INSTALLATION_ID, installationId)
                .apply();

        Log.i("ChatPush", "Firebase messaging registration ready.");

        PushRegistrationManager.getInstance(this).onRegistered(installationId);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Log.i(
                "ChatPush",
                "Push received for message "
                        + message.getData().get("messageId")
        );

        MessageNotifications.show(this, message.getData());
    }

    @Override
    public void onUnregistered(@NonNull String installationId) {
        SharedPreferences preferences =
                getSharedPreferences(PREFS, MODE_PRIVATE);

        if (installationId.equals(
                preferences.getString(KEY_INSTALLATION_ID, ""))) {
            preferences.edit().remove(KEY_INSTALLATION_ID).apply();
        }

        Log.i("ChatPush", "Firebase messaging registration removed.");
    }
}
