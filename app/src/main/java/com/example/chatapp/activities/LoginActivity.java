package com.example.chatapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.chatapp.R;
import com.example.chatapp.database.ChatDatabaseHelper;
import com.example.chatapp.database.User;
import com.example.chatapp.utils.PasswordHasher;
import com.example.chatapp.utils.SessionManager;
import com.google.android.material.button.MaterialButton;

import java.security.GeneralSecurityException;
import java.util.Locale;

public class LoginActivity extends BaseActivity {

    private EditText usernameInput;
    private EditText passwordInput;
    private MaterialButton loginButton;
    private MaterialButton registerButton;
    private ProgressBar progress;

    private ChatDatabaseHelper database;
    private SessionManager sessionManager;
    private boolean loggingIn;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);

        if (sessionManager.isLoggedIn()) {
            openHome();
            return;
        }

        setScreenLayout(R.layout.activity_login);

        database = ChatDatabaseHelper.getInstance(this);

        usernameInput = findViewById(R.id.username);
        passwordInput = findViewById(R.id.password);
        loginButton = findViewById(R.id.btn_login);
        registerButton = findViewById(R.id.btn_register);
        progress = findViewById(R.id.progress);

        loginButton.setOnClickListener(view -> login());

        registerButton.setOnClickListener(view ->
                startActivity(
                        new Intent(this, RegisterActivity.class)
                )
        );

        passwordInput.setOnEditorActionListener(
                (view, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        login();
                        return true;
                    }

                    return false;
                }
        );
    }

    private void login() {
        if (loggingIn) {
            return;
        }

        usernameInput.setError(null);
        passwordInput.setError(null);

        String username = usernameInput.getText().toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        String password = passwordInput.getText().toString();

        if (username.isEmpty()) {
            usernameInput.setError(getString(R.string.enter_username));
            usernameInput.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            passwordInput.setError(getString(R.string.enter_password));
            passwordInput.requestFocus();
            return;
        }

        setLoading(true);

        ChatDatabaseHelper.databaseExecutor.execute(() -> {
            try {
                User user = database.userDao()
                        .getUserByUsername(username);

                if (user == null || !PasswordHasher.verifyPassword(
                        password,
                        user.getPassword()
                )) {
                    showLoginError(R.string.invalid_login);
                    return;
                }

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    setLoading(false);

                    sessionManager.saveSession(user);
                    passwordInput.setText("");

                    openHome();
                });

            } catch (GeneralSecurityException | RuntimeException exception) {
                showLoginError(R.string.login_failed);
            }
        });
    }

    private void showLoginError(@StringRes int message) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            setLoading(false);

            Toast.makeText(
                    LoginActivity.this,
                    message,
                    Toast.LENGTH_LONG
            ).show();
        });
    }

    private void openHome() {
        Intent intent = new Intent(this, HomeActivity.class);

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        loggingIn = loading;

        progress.setVisibility(loading ? View.VISIBLE : View.GONE);

        loginButton.setEnabled(!loading);
        registerButton.setEnabled(!loading);
        usernameInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
    }
}
