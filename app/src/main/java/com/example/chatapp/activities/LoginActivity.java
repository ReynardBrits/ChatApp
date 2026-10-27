package com.example.chatapp.activities;

import android.content.Intent;
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
import com.example.chatapp.network.ApiAccount;
import com.example.chatapp.network.ApiException;
import com.example.chatapp.network.AuthApi;
import com.example.chatapp.utils.SessionManager;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends BaseActivity {

    private static final String LOCAL_NETWORK_PERMISSION =
            "android.permission.ACCESS_LOCAL_NETWORK";

    private final AuthApi auth = new AuthApi();

    private final ExecutorService networkExecutor =
            Executors.newSingleThreadExecutor();

    private EditText usernameInput;
    private EditText passwordInput;
    private MaterialButton loginButton;
    private MaterialButton registerButton;
    private ProgressBar progress;

    private SessionManager sessionManager;
    private boolean loggingIn;
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
                            login();
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
        setScreenLayout(R.layout.activity_login);

        sessionManager = new SessionManager(this);

        usernameInput = findViewById(R.id.username);
        passwordInput = findViewById(R.id.password);
        loginButton = findViewById(R.id.btn_login);
        registerButton = findViewById(R.id.btn_register);
        progress = findViewById(R.id.progress);

        if (savedInstanceState == null) {
            usernameInput.setText(sessionManager.getUsername());
        }

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

        if (sessionManager.isLoggedIn()
                && hasLocalNetworkPermission()) {
            authenticate(auth::getCurrentAccount, true);
        }
    }

    private void login() {
        if (loggingIn || requestingPermission) {
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

        if (!hasLocalNetworkPermission()) {
            requestingPermission = true;
            permissionLauncher.launch(LOCAL_NETWORK_PERMISSION);
            return;
        }

        authenticate(
                () -> auth.login(username, password),
                false
        );
    }

    private boolean hasLocalNetworkPermission() {
        return Build.VERSION.SDK_INT < 37
                || ContextCompat.checkSelfPermission(
                this,
                LOCAL_NETWORK_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void authenticate(
            AccountRequest request,
            boolean restoringSession
    ) {
        setLoading(true);

        networkExecutor.execute(() -> {
            try {
                ApiAccount account = request.execute();

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    sessionManager.saveSession(account);
                    passwordInput.setText("");
                    setLoading(false);
                    openHome();
                });
            } catch (ApiException exception) {
                showLoginError(
                        exception.getMessage(),
                        !restoringSession || exception.isUnauthorized()
                );
            } catch (IOException exception) {
                showLoginError(
                        "Unable to complete login. Check your "
                                + "connection and try again.",
                        !restoringSession
                );
            } catch (RuntimeException exception) {
                showLoginError(
                        getString(R.string.login_failed),
                        !restoringSession
                );
            }
        });
    }

    private void showLoginError(
            String message,
            boolean clearSession
    ) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            if (clearSession) {
                sessionManager.clearSession();
            }

            setLoading(false);

            Toast.makeText(
                    LoginActivity.this,
                    message == null
                            ? getString(R.string.login_failed)
                            : message,
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

    @Override
    protected void onDestroy() {
        networkExecutor.shutdown();
        super.onDestroy();
    }

    @FunctionalInterface
    private interface AccountRequest {
        ApiAccount execute() throws IOException;
    }
}
