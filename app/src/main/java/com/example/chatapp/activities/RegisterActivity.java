package com.example.chatapp.activities;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.chatapp.R;
import com.example.chatapp.network.ApiException;
import com.example.chatapp.network.AuthApi;
import com.example.chatapp.utils.Constants;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegisterActivity extends BaseActivity {

    private static final String LOCAL_NETWORK_PERMISSION =
            "android.permission.ACCESS_LOCAL_NETWORK";

    private final AuthApi auth = new AuthApi();

    private final ExecutorService networkExecutor =
            Executors.newSingleThreadExecutor();

    private EditText usernameInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private MaterialButton registerButton;
    private MaterialButton backButton;
    private ProgressBar progress;

    private boolean registering;
    private boolean requestingPermission;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        requestingPermission = false;

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        if (granted) {
                            register();
                        } else {
                            Toast.makeText(
                                    this,
                                    "Allow Nearby devices access in the app "
                                            + "permissions to connect to the chat server.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
            );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenLayout(R.layout.activity_register);

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
        if (registering || requestingPermission) {
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

        if (Build.VERSION.SDK_INT >= 37
                && ContextCompat.checkSelfPermission(
                this,
                LOCAL_NETWORK_PERMISSION
        ) != PackageManager.PERMISSION_GRANTED) {

            requestingPermission = true;
            permissionLauncher.launch(LOCAL_NETWORK_PERMISSION);
            return;
        }

        setLoading(true);

        networkExecutor.execute(() -> {
            try {
                auth.register(username, password);

                showResult(
                        getString(R.string.registration_success),
                        true
                );
            } catch (ApiException exception) {
                showResult(exception.getMessage(), false);
            } catch (IOException exception) {
                showResult(
                        "Could not confirm registration. Check your "
                                + "connection, then try logging in.",
                        false
                );
            } catch (RuntimeException exception) {
                showResult(
                        getString(R.string.registration_failed),
                        false
                );
            }
        });
    }

    private void showResult(String message, boolean success) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            setLoading(false);

            Toast.makeText(
                    RegisterActivity.this,
                    message == null
                            ? getString(R.string.registration_failed)
                            : message,
                    Toast.LENGTH_LONG
            ).show();

            if (success) {
                passwordInput.setText("");
                confirmPasswordInput.setText("");
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

    @Override
    protected void onDestroy() {
        networkExecutor.shutdown();
        super.onDestroy();
    }
}
