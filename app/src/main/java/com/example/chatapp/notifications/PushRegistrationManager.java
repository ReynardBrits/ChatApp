package com.example.chatapp.notifications;

import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.example.chatapp.network.ApiAccount;
import com.example.chatapp.network.PushInstallationsApi;
import com.example.chatapp.utils.SessionManager;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class PushRegistrationManager {

    private static final String TAG = "ChatPush";

    private static PushRegistrationManager instance;

    private final Context appContext;
    private final PushRegistrationStore store;
    private final SessionManager sessions;
    private final PushInstallationsApi api = new PushInstallationsApi();

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor();

    private PushRegistrationManager(Context context) {
        appContext = context.getApplicationContext();
        store = new PushRegistrationStore(appContext);
        sessions = new SessionManager(appContext);
    }

    public static synchronized PushRegistrationManager getInstance(
            Context context
    ) {
        if (instance == null) {
            instance = new PushRegistrationManager(context);
        }

        return instance;
    }

    public synchronized void sync() {
        if (store.getCurrent() == null) {
            return;
        }

        String installationId =
                ChatMessagingService.getInstallationId(appContext);

        if (!installationId.isEmpty()) {
            onRegistered(installationId);
        }
    }

    public synchronized void onRegistered(String installationId) {
        PushRegistrationStore.Registration registration =
                store.updateInstallationId(installationId);

        if (registration == null || !hasCurrentSession(registration)) {
            return;
        }

        worker.execute(() -> {
            if (!store.isCurrent(registration)
                    || !hasCurrentSession(registration)) {
                return;
            }

            try {
                api.register(
                        registration.getAccount(),
                        registration.getInstallationId(),
                        registration.getBindingId()
                );

                if (store.isCurrent(registration)) {
                    Log.i(
                            TAG,
                            "Notification registration saved for "
                                    + registration.getAccount().getUsername()
                                    + "."
                    );
                }
            } catch (IOException | RuntimeException exception) {
                if (store.isCurrent(registration)) {
                    Log.w(
                            TAG,
                            "Unable to register notifications. "
                                    + "Registration will retry when Home opens.",
                            exception
                    );
                }
            }
        });
    }

    @MainThread
    @Nullable
    public synchronized PushRegistrationStore.Registration
    clearLocalRegistration() {
        PushRegistrationStore.Registration previous = store.clear();

        NotificationManager notifications =
                appContext.getSystemService(NotificationManager.class);

        if (notifications != null) {
            notifications.cancelAll();
        }

        return previous;
    }

    // Call from the logout worker before AuthApi.logout().
    @WorkerThread
    public void unregisterBeforeLogout(
            @Nullable PushRegistrationStore.Registration registration
    ) throws IOException {
        if (registration == null
                || registration.getInstallationId().isEmpty()) {
            return;
        }

        Future<?> cleanup = worker.submit(() -> {
            if (hasCurrentSession(registration)) {
                api.unregister(
                        registration.getAccount(),
                        registration.getInstallationId(),
                        registration.getBindingId()
                );
                Log.i(TAG, "Notification registration removed.");
            }

            return null;
        });

        try {
            cleanup.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "Notification cleanup was interrupted.",
                    exception
            );
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();

            if (cause instanceof IOException) {
                throw (IOException) cause;
            }

            throw new IOException(
                    "Unable to remove the notification registration.",
                    cause
            );
        }
    }

    private boolean hasCurrentSession(
            PushRegistrationStore.Registration registration
    ) {
        ApiAccount account = registration.getAccount();

        return sessions.isLoggedIn()
                && sessions.getUserId() == account.getId()
                && account.getUsername().equals(sessions.getUsername());
    }
}
