package com.example.chatapp.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.example.chatapp.network.ApiAccount;
import com.example.chatapp.network.ServerConfig;

import java.util.UUID;

public final class PushRegistrationStore {

    private static final Object LOCK = new Object();

    private static final String PREFS = "chat_push_binding";
    private static final String SERVER = "server_url";
    private static final String USER_ID = "user_id";
    private static final String USERNAME = "username";
    private static final String INSTALLATION_ID = "installation_id";
    private static final String BINDING_ID = "binding_id";

    private final SharedPreferences preferences;

    public PushRegistrationStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public Registration startSession(ApiAccount account) {
        if (account == null) {
            throw new IllegalArgumentException(
                    "A signed-in account is required."
            );
        }

        synchronized (LOCK) {
            Registration registration = new Registration(
                    account,
                    "",
                    UUID.randomUUID().toString()
            );
            save(registration);
            return registration;
        }
    }

    @Nullable
    public Registration getCurrent() {
        synchronized (LOCK) {
            return readCurrent();
        }
    }

    @Nullable
    public Registration updateInstallationId(String installationId) {
        if (installationId == null
                || !installationId.matches("[A-Za-z0-9_-]{1,256}")) {
            throw new IllegalArgumentException(
                    "Invalid notification installation ID."
            );
        }

        synchronized (LOCK) {
            Registration current = readCurrent();

            if (current == null) {
                return null;
            }

            if (installationId.equals(current.getInstallationId())) {
                return current;
            }

            Registration updated = new Registration(
                    current.getAccount(),
                    installationId,
                    current.getBindingId()
            );
            save(updated);
            return updated;
        }
    }

    public boolean isCurrent(@Nullable Registration registration) {
        synchronized (LOCK) {
            Registration current = readCurrent();

            return registration != null
                    && current != null
                    && current.getBindingId().equals(
                    registration.getBindingId())
                    && current.getInstallationId().equals(
                    registration.getInstallationId())
                    && current.getAccount().getId()
                    == registration.getAccount().getId()
                    && current.getAccount().getUsername().equals(
                    registration.getAccount().getUsername());
        }
    }

    @Nullable
    public Registration clear() {
        synchronized (LOCK) {
            Registration previous = readCurrent();
            preferences.edit().clear().apply();
            return previous;
        }
    }

    private void save(Registration registration) {
        preferences.edit()
                .putString(SERVER, ServerConfig.BASE_URL)
                .putInt(USER_ID, registration.getAccount().getId())
                .putString(
                        USERNAME,
                        registration.getAccount().getUsername()
                )
                .putString(
                        INSTALLATION_ID,
                        registration.getInstallationId()
                )
                .putString(BINDING_ID, registration.getBindingId())
                .apply();
    }

    @Nullable
    private Registration readCurrent() {
        try {
            if (!ServerConfig.BASE_URL.equals(
                    preferences.getString(SERVER, ""))) {
                return null;
            }

            String installationId =
                    preferences.getString(INSTALLATION_ID, "");
            String bindingId =
                    preferences.getString(BINDING_ID, "");

            // Empty means Firebase registration is still pending.
            if (installationId == null
                    || (!installationId.isEmpty()
                    && !installationId.matches("[A-Za-z0-9_-]{1,256}"))
                    || bindingId == null
                    || !bindingId.matches("[A-Za-z0-9_-]{16,128}")) {
                return null;
            }

            ApiAccount account = new ApiAccount(
                    preferences.getInt(USER_ID, -1),
                    preferences.getString(USERNAME, "")
            );

            return new Registration(account, installationId, bindingId);

        } catch (IllegalArgumentException | ClassCastException exception) {
            return null;
        }
    }

    public static final class Registration {

        private final ApiAccount account;
        private final String installationId;
        private final String bindingId;

        private Registration(
                ApiAccount account,
                String installationId,
                String bindingId
        ) {
            this.account = account;
            this.installationId = installationId;
            this.bindingId = bindingId;
        }

        public ApiAccount getAccount() {
            return account;
        }

        public String getInstallationId() {
            return installationId;
        }

        public String getBindingId() {
            return bindingId;
        }
    }
}
