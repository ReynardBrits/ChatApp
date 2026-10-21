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
import com.example.chatapp.utils.Constants;
import com.example.chatapp.utils.SessionManager;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends BaseActivity {

    private SessionManager sessionManager;

    private Spinner usersSpinner;
    private ArrayAdapter<User> usersAdapter;
    private MaterialButton conversationButton;
    private MaterialButton cameraButton;
    private TextView noUsersText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            logout();
            return;
        }

        setScreenLayout(R.layout.activity_home);

        TextView welcomeText = findViewById(R.id.welcome);
        usersSpinner = findViewById(R.id.users_spinner);
        conversationButton = findViewById(R.id.btn_conversations);
        cameraButton = findViewById(R.id.btn_camera);
        noUsersText = findViewById(R.id.no_users);

        welcomeText.setText(
                getString(
                        R.string.welcome_user,
                        sessionManager.getUsername()
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

        // Wait for the database to supply the recipient list.
        usersSpinner.setEnabled(false);
        conversationButton.setEnabled(false);
        cameraButton.setEnabled(false);

        conversationButton.setOnClickListener(
                view -> openConversation(false)
        );

        cameraButton.setOnClickListener(
                view -> openConversation(true)
        );

        findViewById(R.id.btn_logout).setOnClickListener(
                view -> logout()
        );

        ChatDatabaseHelper database =
                ChatDatabaseHelper.getInstance(this);

        database.userDao()
                .getOtherUsers(sessionManager.getUserId())
                .observe(this, this::updateUsers);
    }

    private void updateUsers(List<User> users) {
        User selectedUser =
                (User) usersSpinner.getSelectedItem();

        String selectedUsername = selectedUser == null
                ? null
                : selectedUser.getUsername();

        usersAdapter.clear();

        if (users != null) {
            usersAdapter.addAll(users);
        }

        boolean hasUsers = usersAdapter.getCount() > 0;

        usersSpinner.setEnabled(hasUsers);
        conversationButton.setEnabled(hasUsers);
        cameraButton.setEnabled(hasUsers);

        noUsersText.setVisibility(
                hasUsers ? View.GONE : View.VISIBLE
        );

        // Keep the selected recipient when the list refreshes.
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
    }

    private void openConversation(boolean openCamera) {
        if (!sessionManager.isLoggedIn()) {
            logout();
            return;
        }

        User recipient =
                (User) usersSpinner.getSelectedItem();

        if (recipient == null
                || recipient.getId() == sessionManager.getUserId()) {
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

        intent.putExtra(
                Constants.EXTRA_OPEN_CAMERA,
                openCamera
        );

        startActivity(intent);
    }

    private void logout() {
        sessionManager.clearSession();

        Intent intent = new Intent(this, LoginActivity.class);

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}