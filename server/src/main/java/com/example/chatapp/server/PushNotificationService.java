package com.example.chatapp.server;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;

import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PushNotificationService implements AutoCloseable {

    private static final Logger LOGGER =
            Logger.getLogger(PushNotificationService.class.getName());

    private static final AndroidConfig ANDROID = AndroidConfig.builder()
            .setPriority(AndroidConfig.Priority.HIGH)
            .setRestrictedPackageName("com.example.chatapp")
            .setTtl(TimeUnit.HOURS.toMillis(1))
            .build();

    private final PushInstallationRepository installations;

    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(256),
            task -> {
                Thread thread = new Thread(task, "chat-push-sender");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    public PushNotificationService(ServerDatabase database) {
        installations = new PushInstallationRepository(database);
    }

    public void notifyNewMessage(MessageRepository.ChatMessage message) {
        if (message == null || FirebaseSetup.getMessaging() == null) {
            return;
        }

        try {
            worker.execute(() -> deliver(message));
        } catch (RejectedExecutionException exception) {
            LOGGER.warning(
                    "Notification queue unavailable for message " + message.id()
            );
        }
    }

    private void deliver(MessageRepository.ChatMessage message) {
        FirebaseMessaging messaging = FirebaseSetup.getMessaging();

        if (messaging == null || Thread.currentThread().isInterrupted()) {
            return;
        }

        try {
            for (PushInstallationRepository.Registration registration
                    : installations.findForUser(message.receiver())) {

                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                sendOne(messaging, registration, message);
            }
        } catch (SQLException | RuntimeException exception) {
            LOGGER.log(
                    Level.WARNING,
                    "Unable to load notification recipients for message "
                            + message.id(),
                    exception
            );
        }
    }

    private void sendOne(
            FirebaseMessaging messaging,
            PushInstallationRepository.Registration registration,
            MessageRepository.ChatMessage message
    ) {
        try {
            Message notification = Message.builder()
                    .setFid(registration.installationId())
                    .setAndroidConfig(ANDROID)
                    .putData("type", "private_message")
                    .putData("messageId", Long.toString(message.id()))
                    .putData("sender", message.sender())
                    .putData("receiver", message.receiver())
                    .putData("bindingId", registration.bindingId())
                    .putData("preview", preview(message))
                    .putData("timestamp", Long.toString(message.timestamp()))
                    .build();

            messaging.send(notification);

            LOGGER.info(
                    "Firebase accepted notification for message "
                            + message.id() + " to "
                            + registration.username() + "."
            );
        } catch (FirebaseMessagingException exception) {
            if (exception.getMessagingErrorCode()
                    == MessagingErrorCode.UNREGISTERED) {
                removeExpired(registration);
                return;
            }

            LOGGER.log(
                    Level.WARNING,
                    "Firebase notification failed for message " + message.id(),
                    exception
            );
        } catch (RuntimeException exception) {
            LOGGER.log(
                    Level.WARNING,
                    "Unable to prepare notification for message " + message.id(),
                    exception
            );
        }
    }

    private void removeExpired(
            PushInstallationRepository.Registration registration
    ) {
        try {
            installations.unbind(
                    registration.username(),
                    registration.installationId(),
                    registration.bindingId()
            );
            LOGGER.info("Removed an expired notification registration.");
        } catch (SQLException | RuntimeException exception) {
            LOGGER.log(
                    Level.WARNING,
                    "Unable to remove an expired notification registration.",
                    exception
            );
        }
    }

    private static String preview(MessageRepository.ChatMessage message) {
        if (message.imageName() != null) {
            return "Photo";
        }

        String text = message.content() == null ? ""
                : message.content().replace('\n', ' ').replace('\r', ' ').trim();

        if (text.isEmpty()) {
            return "New message";
        }

        if (text.codePointCount(0, text.length()) <= 120) {
            return text;
        }

        return text.substring(0, text.offsetByCodePoints(0, 120)) + "…";
    }

    @Override
    public void close() {
        worker.shutdown();

        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException exception) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}