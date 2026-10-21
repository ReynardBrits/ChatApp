package com.example.chatapp.activities;

import android.database.sqlite.SQLiteConstraintException;
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
import com.example.chatapp.utils.Constants;
import com.example.chatapp.utils.PasswordHasher;
import com.google.android.material.button.MaterialButton;

import java.security.GeneralSecurityException;
import java.util.Locale;

public class RegisterActivity extends BaseActivity {

    private EditText usernameInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private MaterialButton registerButton;
    private MaterialButton backButton;
    private ProgressBar progress;

    private ChatDatabaseHelper database;
    private boolean registering;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenLayout(R.layout.activity_register);

        database = ChatDatabaseHelper.getInstance(this);

        usernameInput = findViewById(R.id.username);
        passwordInput = findViewById(R.id.password);
        confirmPasswordInput = findViewById(R.id.confirm_password);
        registerButton = findViewById(R.id.btn_register);
        backButton = findViewById(R.id.btn_back_login);
        progress = findViewById(R.id.progress);

        registerButton.setOnClickListener(view -> register());
        backButton.setOnClickListener(view -> finish());

        confirmPasswordInput.setOnEditorActionListener(
                (view, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        register();
                        return true;
                    }

                    return false;
                }
        );
    }

    private void register() {
        if (registering) {
            return;
        }

        usernameInput.setError(null);
        passwordInput.setError(null);
        confirmPasswordInput.setError(null);

        String username = usernameInput.getText().toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        String password = passwordInput.getText().toString();
        String confirmation = confirmPasswordInput.getText().toString();

        if (!username.matches("[a-z0-9_]{3,24}")) {
            usernameInput.setError(getString(R.string.username_rule));
            usernameInput.requestFocus();
            return;
        }

        if (password.length() < Constants.MIN_PASSWORD_LENGTH
                || password.length() > Constants.MAX_PASSWORD_LENGTH) {
            passwordInput.setError(getString(R.string.password_rule));
            passwordInput.requestFocus();
            return;
        }

        if (!password.equals(confirmation)) {
            confirmPasswordInput.setError(
                    getString(R.string.password_mismatch)
            );
            confirmPasswordInput.requestFocus();
            return;
        }

        setLoading(true);

        // Database work and password hashing run off the UI thread.
        ChatDatabaseHelper.databaseExecutor.execute(() -> {
            try {
                User existingUser = database.userDao()
                        .getUserByUsername(username);

                if (existingUser != null) {
                    showResult(R.string.username_taken, false);
                    return;
                }

                User user = new User();
                user.setUsername(username);
                user.setPassword(
                        PasswordHasher.hashPassword(password)
                );

                database.userDao().insertUser(user);

                showResult(R.string.registration_success, true);

            } catch (SQLiteConstraintException exception) {
                showResult(R.string.username_taken, false);

            } catch (GeneralSecurityException | RuntimeException exception) {
                showResult(R.string.registration_failed, false);
            }
        });
    }

    private void showResult(@StringRes int message, boolean success) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            setLoading(false);

            Toast.makeText(
                    RegisterActivity.this,
                    message,
                    Toast.LENGTH_LONG
            ).show();

            if (success) {
                // Return to the login screen after registration.
                finish();
            }
        });
    }

    private void setLoading(boolean loading) {
        registering = loading;

        progress.setVisibility(loading ? View.VISIBLE : View.GONE);

        registerButton.setEnabled(!loading);
        backButton.setEnabled(!loading);
        usernameInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        confirmPasswordInput.setEnabled(!loading);
    }
}
