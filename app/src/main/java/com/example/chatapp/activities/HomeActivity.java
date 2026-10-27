package com.example.chatapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.chatapp.R;
import com.example.chatapp.database.ChatDatabaseHelper;
import com.example.chatapp.database.User;
import com.example.chatapp.network.ApiAccount;
import com.example.chatapp.network.ApiException;
import com.example.chatapp.network.AuthApi;
import com.example.chatapp.network.UsersApi;
import com.example.chatapp.utils.Constants;
import com.example.chatapp.utils.SessionManager;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivity extends BaseActivity {

    private final AuthApi auth = new AuthApi();
    private final UsersApi usersApi = new UsersApi();

    private final ExecutorService networkExecutor =
            Executors.newSingleThreadExecutor();

    private SessionManager sessionManager;
    private ChatDatabaseHelper database;
    private ApiAccount currentAccount;

    private Spinner usersSpinner;
    private ArrayAdapter<User> usersAdapter;
    private MaterialButton conversationButton;
    private MaterialButton cameraButton;
    private MaterialButton logoutButton;
    private TextView noUsersText;

    private boolean refreshing;
    private boolean loggingOut;
    private boolean leaving;
    private String loadError;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            returnToLogin();
            return;
        }

        currentAccount = new ApiAccount(
                sessionManager.getUserId(),
                sessionManager.getUsername()
        );

        setScreenLayout(R.layout.activity_home);

        TextView welcomeText = findViewById(R.id.welcome);
        usersSpinner = findViewById(R.id.users_spinner);
        conversationButton = findViewById(R.id.btn_conversations);
        cameraButton = findViewById(R.id.btn_camera);
        logoutButton = findViewById(R.id.btn_logout);
        noUsersText = findViewById(R.id.no_users);

        welcomeText.setText(
                getString(
                        R.string.welcome_user,
                        currentAccount.getUsername()
                )
        );

        usersAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new ArrayList<>()
        );

        usersAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        usersSpinner.setAdapter(usersAdapter);

        conversationButton.setOnClickListener(
                view -> openConversation(false)
        );

        cameraButton.setOnClickListener(
                view -> openConversation(true)
        );

        logoutButton.setOnClickListener(view -> logout());

        noUsersText.setOnClickListener(view -> refreshUsers());
        noUsersText.setFocusable(true);

        try {
            database = ChatDatabaseHelper.getInstance(this);
        } catch (IllegalStateException exception) {
            returnToLogin();
            return;
        }

        database.userDao()
                .getOtherUsers(currentAccount.getId())
                .observe(this, this::updateUsers);

        updateControls();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (database != null && canUpdateScreen()) {
            refreshUsers();
        }
    }

    private void refreshUsers() {
        if (!canUpdateScreen() || refreshing) {
            return;
        }

        if (!sameAccount()) {
            returnToLogin();
            return;
        }

        refreshing = true;
        loadError = null;
        updateControls();

        networkExecutor.execute(() -> {
            try {
                List<ApiAccount> accounts = usersApi.getOtherUsers();
                List<User> cachedUsers = new ArrayList<>();

                // Keep the signed-in user for message foreign keys.
                cachedUsers.add(toCachedUser(currentAccount));

                for (ApiAccount account : accounts) {
                    cachedUsers.add(toCachedUser(account));
                }

                if (!sameAccount()) {
                    runOnUiThread(() -> {
                        if (canUpdateScreen()) {
                            returnToLogin();
                        }
                    });
                    return;
                }

                database.userDao().upsertUsers(cachedUsers);

                runOnUiThread(() -> {
                    if (!canUpdateScreen()) {
                        return;
                    }

                    refreshing = false;

                    if (!sameAccount()) {
                        returnToLogin();
                        return;
                    }

                    updateControls();
                });
            } catch (ApiException exception) {
                showRefreshError(
                        exception.getMessage(),
                        exception.isUnauthorized()
                );
            } catch (IOException exception) {
                showRefreshError(
                        "Could not refresh users. Check your connection.",
                        false
                );
            } catch (RuntimeException exception) {
                showRefreshError(
                        "Could not update saved accounts. Please try again.",
                        false
                );
            }
        });
    }

    private static User toCachedUser(ApiAccount account) {
        User user = new User();
        user.setId(account.getId());
        user.setUsername(account.getUsername());
        return user;
    }

    private void updateUsers(List<User> users) {
        if (!canUpdateScreen()) {
            return;
        }

        User selected = (User) usersSpinner.getSelectedItem();

        String selectedUsername = selected == null
                ? null
                : selected.getUsername();

        usersAdapter.clear();

        if (users != null) {
            usersAdapter.addAll(users);
        }

        if (selectedUsername != null) {
            for (int index = 0; index < usersAdapter.getCount(); index++) {
                User user = usersAdapter.getItem(index);

                if (user != null
                        && selectedUsername.equals(user.getUsername())) {
                    usersSpinner.setSelection(index);
                    break;
                }
            }
        }

        updateControls();
    }

    private void updateControls() {
        boolean hasUsers = usersAdapter.getCount() > 0;
        boolean canChat = hasUsers && !loggingOut && sameAccount();

        usersSpinner.setEnabled(canChat);
        conversationButton.setEnabled(canChat);
        cameraButton.setEnabled(canChat);
        logoutButton.setEnabled(!loggingOut);

        noUsersText.setVisibility(
                hasUsers ? View.GONE : View.VISIBLE
        );

        if (!hasUsers) {
            if (refreshing) {
                noUsersText.setText("Loading users…");
            } else if (loadError != null) {
                noUsersText.setText(loadError + " Tap here to retry.");
            } else {
                noUsersText.setText(R.string.no_users);
            }
        }
    }

    private void showRefreshError(String message, boolean unauthorized) {
        runOnUiThread(() -> {
            if (!canUpdateScreen()) {
                return;
            }

            refreshing = false;

            if (unauthorized || !sameAccount()) {
                returnToLogin();
                return;
            }

            loadError = message == null
                    ? "Could not load users."
                    : message;

            updateControls();

            if (usersAdapter.getCount() > 0) {
                Toast.makeText(
                        this,
                        loadError,
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void openConversation(boolean openCamera) {
        if (!canUpdateScreen()) {
            return;
        }

        if (!sameAccount()) {
            returnToLogin();
            return;
        }

        User recipient = (User) usersSpinner.getSelectedItem();

        if (recipient == null
                || recipient.getId() == currentAccount.getId()
                || recipient.getUsername().equals(
                currentAccount.getUsername()
        )) {
            Toast.makeText(
                    this,
                    R.string.invalid_receiver,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Intent intent = new Intent(this, ConversationActivity.class);

        intent.putExtra(
                Constants.EXTRA_RECEIVER,
                recipient.getUsername()
        );

        intent.putExtra(Constants.EXTRA_OPEN_CAMERA, openCamera);

        startActivity(intent);
    }

    private void logout() {
        if (!canUpdateScreen()) {
            return;
        }

        loggingOut = true;
        logoutButton.setText("Logging out…");
        updateControls();

        networkExecutor.execute(() -> {
            try {
                auth.logout();
            } catch (IOException | RuntimeException ignored) {
                // AuthApi clears the cookie even if the request fails.
            } finally {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        returnToLogin();
                    }
                });
            }
        });
    }

    private boolean sameAccount() {
        return sessionManager.isLoggedIn()
                && sessionManager.getUserId() == currentAccount.getId()
                && currentAccount.getUsername().equals(
                sessionManager.getUsername()
        );
    }

    private boolean canUpdateScreen() {
        return !isFinishing()
                && !isDestroyed()
                && !leaving
                && !loggingOut;
    }

    private void returnToLogin() {
        if (leaving) {
            return;
        }

        leaving = true;
        sessionManager.clearSession();

        Intent intent = new Intent(this, LoginActivity.class);

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        networkExecutor.shutdown();
        super.onDestroy();
    }
}