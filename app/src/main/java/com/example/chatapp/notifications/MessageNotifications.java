package com.example.chatapp.notifications;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.net.Uri;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.chatapp.ChatApp;
import com.example.chatapp.R;
import com.example.chatapp.activities.HomeActivity;

import java.util.HashMap;
import java.util.Map;

public final class MessageNotifications {

    public static final String ACTION_OPEN_CONVERSATION =
            "com.example.chatapp.OPEN_CONVERSATION";

    public static final String EXTRA_ACCOUNT = "notification_account";
    public static final String EXTRA_BINDING = "notification_binding";
    public static final String EXTRA_SENDER = "notification_sender";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private MessageNotifications() {
    }

    public static void show(Context context, Map<String, String> data) {
        Context app = context.getApplicationContext();
        Map<String, String> snapshot = new HashMap<>(data);

        MAIN.post(() -> showNow(app, snapshot));
    }

    private static void showNow(Context context, Map<String, String> data) {
        if (!"private_message".equals(data.get("type"))) {
            return;
        }

        PushRegistrationStore store = new PushRegistrationStore(context);
        PushRegistrationStore.Registration registration = store.getCurrent();

        if (registration == null
                || !registration.getAccount().getUsername().equals(
                data.get("receiver"))
                || !registration.getBindingId().equals(data.get("bindingId"))) {
            return;
        }

        String sender = data.get("sender");

        if (sender == null
                || !sender.matches("[a-z0-9_]{3,24}")
                || sender.equals(registration.getAccount().getUsername())) {
            return;
        }

        long messageId;
        long timestamp;

        try {
            messageId = Long.parseLong(data.get("messageId"));
            timestamp = Long.parseLong(data.get("timestamp"));
        } catch (NumberFormatException exception) {
            return;
        }

        if (messageId <= 0 || timestamp <= 0) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {
            Log.i("ChatPush", "Notification permission is off.");
            return;
        }

        NotificationManager notifications =
                context.getSystemService(NotificationManager.class);

        if (notifications == null || !notifications.areNotificationsEnabled()) {
            Log.i("ChatPush", "Notifications are disabled in Android settings.");
            return;
        }

        Intent openHome = new Intent(context, HomeActivity.class);
        openHome.setAction(ACTION_OPEN_CONVERSATION);

        openHome.setData(new Uri.Builder()
                .scheme("chatapp")
                .authority("notification")
                .appendPath(registration.getBindingId())
                .appendPath(sender)
                .build());

        openHome.putExtra(
                EXTRA_ACCOUNT,
                registration.getAccount().getUsername()
        );
        openHome.putExtra(EXTRA_BINDING, registration.getBindingId());
        openHome.putExtra(EXTRA_SENDER, sender);

        openHome.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        PendingIntent openApp = PendingIntent.getActivity(
                context,
                0,
                openHome,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String preview = data.get("preview");

        if (preview == null || preview.trim().isEmpty()) {
            preview = "New message";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context,
                ChatApp.MESSAGE_CHANNEL_ID
        )
                .setSmallIcon(R.drawable.ic_chat_notification)
                .setColor(ContextCompat.getColor(context, R.color.chat_primary))
                .setContentTitle(sender)
                .setContentText(preview)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(preview))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setWhen(timestamp);

        if (!store.isCurrent(registration)) {
            return;
        }

        String tag = "private:"
                + registration.getBindingId()
                + ":" + messageId;

        notifications.notify(tag, 1, builder.build());
        Log.i("ChatPush", "Notification shown for message " + messageId + ".");
    }
}
