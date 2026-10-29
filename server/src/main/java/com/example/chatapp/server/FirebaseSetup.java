package com.example.chatapp.server;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.IOException;

public final class FirebaseSetup {

    private static FirebaseApp app;
    private static FirebaseMessaging messaging;

    private FirebaseSetup() {}

    public static synchronized void initialize() throws IOException {
        if (messaging != null) {
            return;
        }

        String credentialPath =
                System.getenv("GOOGLE_APPLICATION_CREDENTIALS");

        if (credentialPath == null || credentialPath.isBlank()) {
            System.out.println(
                    "Firebase notifications disabled: "
                            + "GOOGLE_APPLICATION_CREDENTIALS is not set."
            );
            return;
        }

        GoogleCredentials credentials =
                GoogleCredentials.getApplicationDefault();

        if (!(credentials instanceof ServiceAccountCredentials account)) {
            throw new IOException(
                    "Use the Firebase service-account JSON key."
            );
        }

        String projectId = account.getProjectId();

        if (projectId == null || projectId.isBlank()) {
            throw new IOException(
                    "The Firebase key does not contain a project ID."
            );
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .setConnectTimeout(10000)
                .setReadTimeout(10000)
                .setWriteTimeout(10000)
                .build();

        app = FirebaseApp.initializeApp(options, "chatapp-notifications");
        messaging = FirebaseMessaging.getInstance(app);

        System.out.println(
                "Firebase notifications configured for project: " + projectId
        );
    }

    // Returns null when Firebase notifications have not been configured.
    public static synchronized FirebaseMessaging getMessaging() {
        return messaging;
    }

    public static synchronized void close() {
        FirebaseApp previous = app;
        app = null;
        messaging = null;

        if (previous != null) {
            previous.delete();
        }
    }
}