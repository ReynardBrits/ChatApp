package com.example.chatapp;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

public final class ChatApp extends Application {

    public static final String MESSAGE_CHANNEL_ID = "private_messages";

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationManager manager =
                getSystemService(NotificationManager.class);

        if (manager == null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Private messages",
                NotificationManager.IMPORTANCE_HIGH
        );

        channel.setDescription(
                "Notifications for new private chat messages."
        );
        channel.setShowBadge(true);

        manager.createNotificationChannel(channel);
    }
}