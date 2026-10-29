package com.example.chatapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatapp.R;
import com.example.chatapp.adapters.ConversationAdapter;
import com.example.chatapp.network.ApiAccount;
import com.example.chatapp.network.AuthApi;
import com.example.chatapp.utils.Constants;
import com.example.chatapp.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivity extends BaseActivity {

    private final ExecutorService logoutWorker =
            Executors.newSingleThreadExecutor();

    private final AuthApi auth = new AuthApi();

    private SessionManager sessionManager;
    private ApiAccount currentAccount;
    private HomeViewModel viewModel;
    private HomeViewModel.UiState uiState;
    private ConversationAdapter adapter;

    private TextView statusText;
    private TextView emptyText;
    private View progress;
    private MaterialButton newChatButton;
    private MaterialButton refreshButton;
    private MaterialButton logoutButton;
    private AlertDialog userDialog;

    private boolean loggingOut;
    private boolean leaving;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            returnToLogin();
            return;
        }

        setScreenLayout(R.layout.activity_home);

        try {
            viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
            currentAccount = viewModel.getCurrentAccount();
        } catch (RuntimeException exception) {
            showToast("Unable to open Home. Please sign in again.");
            returnToLogin();
            return;
        }

        TextView welcome = findViewById(R.id.welcome);
        RecyclerView list = findViewById(R.id.conversation_list);
        statusText = findViewById(R.id.home_status);
        emptyText = findViewById(R.id.empty_conversations);
        progress = findViewById(R.id.home_progress);
        newChatButton = findViewById(R.id.btn_new_chat);
        refreshButton = findViewById(R.id.btn_refresh);
        logoutButton = findViewById(R.id.btn_logout);

        welcome.setText(getString(
                R.string.welcome_user,
                currentAccount.getUsername()
        ));

        adapter = new ConversationAdapter(
                currentAccount.getUsername(),
                this::openConversation
        );

        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        list.addItemDecoration(new DividerItemDecoration(
                this,
                DividerItemDecoration.VERTICAL
        ));

        newChatButton.setOnClickListener(view -> chooseUser());

        refreshButton.setOnClickListener(view -> {
            if (checkSession()) {
                viewModel.refresh();
            }
        });

        logoutButton.setOnClickListener(view -> logout());

        viewModel.getState().observe(this, this::render);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (viewModel != null && checkSession()) {
            viewModel.start();
        }
    }

    @Override
    protected void onStop() {
        dismissUserDialog();

        if (viewModel != null) {
            viewModel.stop();
        }

        super.onStop();
    }

    private void render(HomeViewModel.UiState state) {
        if (!canUpdateUi()) {
            return;
        }

        uiState = state;

        if (!loggingOut && (state.sessionExpired || !sameAccount())) {
            returnToLogin();
            return;
        }

        adapter.setConversations(state.conversations);

        progress.setVisibility(
                state.refreshing ? View.VISIBLE : View.INVISIBLE
        );

        statusText.setVisibility(
                state.error == null ? View.GONE : View.VISIBLE
        );
        statusText.setText(state.error == null ? "" : state.error);

        emptyText.setVisibility(
                state.conversations.isEmpty()
                        && !state.refreshing
                        && state.error == null
                        ? View.VISIBLE
                        : View.GONE
        );

        newChatButton.setEnabled(
                !loggingOut
                        && (!state.users.isEmpty() || !state.refreshing)
        );
        refreshButton.setEnabled(!loggingOut && !state.refreshing);
        logoutButton.setEnabled(!loggingOut);
        logoutButton.setText(
                loggingOut ? "Logging out…" : getString(R.string.logout)
        );
    }

    private void chooseUser() {
        if (!checkSession() || uiState == null || userDialog != null) {
            return;
        }

        List<ApiAccount> options = new ArrayList<>(uiState.users);

        if (options.isEmpty()) {
            showToast(
                    "No other accounts yet. Register another user, then tap Refresh."
            );
            return;
        }

        String[] names = new String[options.size()];

        for (int index = 0; index < options.size(); index++) {
            names[index] = options.get(index).getUsername();
        }

        userDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("New chat")
                .setItems(names, (dialog, which) ->
                        openConversation(options.get(which))
                )
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        userDialog.setOnDismissListener(dialog -> userDialog = null);
        userDialog.show();
    }

    private void openConversation(ApiAccount recipient) {
        if (!checkSession()) {
            return;
        }

        if (recipient.getId() == currentAccount.getId()
                || recipient.getUsername().equals(
                currentAccount.getUsername()
        )) {
            showToast(getString(R.string.invalid_receiver));
            return;
        }

        Intent intent = new Intent(this, ConversationActivity.class);
        intent.putExtra(Constants.EXTRA_RECEIVER, recipient.getUsername());
        intent.putExtra(Constants.EXTRA_OPEN_CAMERA, false);
        startActivity(intent);
    }

    private void logout() {
        if (!checkSession()) {
            return;
        }

        loggingOut = true;
        dismissUserDialog();
        viewModel.stop();

        logoutWorker.execute(() -> {
            try {
                if (sameAccount()) {
                    auth.logout();
                }
            } catch (IOException | RuntimeException ignored) {
                // AuthApi clears the cookie even if the request fails.
            } finally {
                runOnUiThread(() -> {
                    if (canUpdateUi()) {
                        returnToLogin();
                    }
                });
            }
        });
    }

    private boolean sameAccount() {
        return currentAccount != null
                && sessionManager.isLoggedIn()
                && sessionManager.getUserId() == currentAccount.getId()
                && currentAccount.getUsername().equals(
                sessionManager.getUsername()
        );
    }

    private boolean checkSession() {
        if (!canUpdateUi() || loggingOut) {
            return false;
        }

        if (!sameAccount()) {
            returnToLogin();
            return false;
        }

        return true;
    }

    private void returnToLogin() {
        if (leaving) {
            return;
        }

        leaving = true;

        if (viewModel != null) {
            viewModel.stop();
        }

        // An older screen must not clear a different account's session.
        if (currentAccount != null
                && sessionManager.getUserId() > 0
                && (sessionManager.getUserId() != currentAccount.getId()
                || !currentAccount.getUsername().equals(
                sessionManager.getUsername()
        ))) {
            finish();
            return;
        }

        sessionManager.clearSession();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );
        startActivity(intent);
        finish();
    }

    private boolean canUpdateUi() {
        return !leaving && !isFinishing() && !isDestroyed();
    }

    private void showToast(String message) {
        if (canUpdateUi()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    private void dismissUserDialog() {
        if (userDialog != null) {
            userDialog.dismiss();
            userDialog = null;
        }
    }

    @Override
    protected void onDestroy() {
        dismissUserDialog();
        logoutWorker.shutdown();
        super.onDestroy();
    }
}