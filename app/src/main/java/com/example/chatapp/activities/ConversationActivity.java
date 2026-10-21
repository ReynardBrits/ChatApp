package com.example.chatapp.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatapp.R;
import com.example.chatapp.adapters.MessageAdapter;
import com.example.chatapp.database.ChatDatabaseHelper;
import com.example.chatapp.database.Message;
import com.example.chatapp.database.User;
import com.example.chatapp.utils.Constants;
import com.example.chatapp.utils.SessionManager;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.IOException;

public class ConversationActivity extends BaseActivity {

    private ChatDatabaseHelper database;
    private SessionManager sessionManager;

    private RecyclerView recyclerView;
    private MessageAdapter adapter;
    private EditText messageInput;
    private TextView emptyText;
    private MaterialButton sendButton;
    private MaterialButton cameraButton;

    private String currentUser;
    private int currentUserId;
    private String receiver;
    private String pendingPhotoPath;

    private boolean ready;
    private boolean sending;
    private boolean autoOpenCamera;

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.TakePicture(),
                    this::handlePhotoResult
            );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            openLogin();
            return;
        }

        currentUser = sessionManager.getUsername();
        currentUserId = sessionManager.getUserId();
        receiver = getIntent().getStringExtra(Constants.EXTRA_RECEIVER);

        if (receiver == null
                || receiver.trim().isEmpty()
                || receiver.equals(currentUser)) {
            showToast(R.string.invalid_receiver);
            finish();
            return;
        }

        setScreenLayout(R.layout.activity_conversation);
        database = ChatDatabaseHelper.getInstance(this);

        if (savedInstanceState != null) {
            pendingPhotoPath = savedInstanceState.getString(
                    Constants.STATE_PHOTO_PATH
            );

            autoOpenCamera = savedInstanceState.getBoolean(
                    Constants.EXTRA_OPEN_CAMERA,
                    false
            );
        } else {
            autoOpenCamera = getIntent().getBooleanExtra(
                    Constants.EXTRA_OPEN_CAMERA,
                    false
            );
        }

        TextView title = findViewById(R.id.chat_title);
        recyclerView = findViewById(R.id.recyclerView);
        messageInput = findViewById(R.id.message);
        emptyText = findViewById(R.id.empty_conversation);
        sendButton = findViewById(R.id.btn_send);
        cameraButton = findViewById(R.id.btn_camera);

        title.setText(getString(R.string.chat_with, receiver));

        adapter = new MessageAdapter(currentUser);

        LinearLayoutManager layoutManager =
                new LinearLayoutManager(this);

        layoutManager.setStackFromEnd(true);

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btn_back_home).setOnClickListener(
                view -> finish()
        );

        sendButton.setOnClickListener(view -> sendText());
        cameraButton.setOnClickListener(view -> openCamera());

        updateControls();
        loadConversation();
    }

    private void loadConversation() {
        ChatDatabaseHelper.databaseExecutor.execute(() -> {
            try {
                User recipient = database.userDao()
                        .getUserByUsername(receiver);

                if (recipient == null) {
                    runOnUiThread(() ->
                            closeWithError(R.string.invalid_receiver)
                    );
                    return;
                }

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    ready = true;
                    updateControls();

                    database.messageDao()
                            .getConversation(currentUser, receiver)
                            .observe(this, messages -> {
                                adapter.setMessages(messages);

                                int count = adapter.getItemCount();

                                emptyText.setVisibility(
                                        count == 0 ? View.VISIBLE : View.GONE
                                );

                                if (count > 0) {
                                    recyclerView.scrollToPosition(count - 1);
                                }
                            });

                    if (autoOpenCamera) {
                        autoOpenCamera = false;
                        openCamera();
                    }
                });

            } catch (RuntimeException exception) {
                runOnUiThread(() ->
                        closeWithError(R.string.conversation_failed)
                );
            }
        });
    }

    private void sendText() {
        if (!ready || sending || pendingPhotoPath != null) {
            return;
        }

        String content = messageInput.getText().toString().trim();

        if (content.isEmpty()) {
            messageInput.setError(getString(R.string.enter_message));
            return;
        }

        if (content.length() > Constants.MAX_MESSAGE_LENGTH) {
            messageInput.setError(getString(R.string.message_too_long));
            return;
        }

        saveMessage(content, null);
    }

    private void saveMessage(String content, @Nullable String imagePath) {
        sending = true;
        updateControls();

        Message message = new Message();
        message.setSender(currentUser);
        message.setReceiver(receiver);
        message.setContent(content);
        message.setTimestamp(System.currentTimeMillis());
        message.setImagePath(imagePath);

        ChatDatabaseHelper.databaseExecutor.execute(() -> {
            try {
                User sender = database.userDao()
                        .getUserByUsername(currentUser);

                if (sender == null
                        || sender.getId() != currentUserId
                        || sessionManager.getUserId() != currentUserId) {
                    throw new IllegalStateException("Invalid session");
                }

                database.messageDao().insertMessage(message);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (imagePath == null) {
                        messageInput.setText("");
                    }

                    sending = false;
                    updateControls();
                });

            } catch (RuntimeException exception) {
                deletePhoto(imagePath);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    sending = false;
                    updateControls();
                    showToast(R.string.message_failed);
                });
            }
        });
    }

    private void openCamera() {
        if (!ready || sending || pendingPhotoPath != null) {
            return;
        }

        try {
            File directory = new File(
                    getFilesDir(),
                    Constants.IMAGE_DIRECTORY
            );

            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Unable to create photo directory");
            }

            File photo = File.createTempFile(
                    "chat_",
                    ".jpg",
                    directory
            );

            pendingPhotoPath = photo.getAbsolutePath();

            Uri photoUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    photo
            );

            updateControls();
            cameraLauncher.launch(photoUri);

        } catch (IOException | RuntimeException exception) {
            deletePhoto(pendingPhotoPath);
            pendingPhotoPath = null;
            updateControls();

            showToast(
                    exception instanceof ActivityNotFoundException
                            ? R.string.no_camera
                            : R.string.camera_failed
            );
        }
    }

    private void handlePhotoResult(boolean saved) {
        String photoPath = pendingPhotoPath;
        pendingPhotoPath = null;

        if (photoPath == null) {
            return;
        }

        File photo = new File(photoPath);

        if (saved && photo.isFile() && photo.length() > 0) {
            saveMessage("", photoPath);
        } else {
            deletePhoto(photoPath);
            updateControls();

            if (saved) {
                showToast(R.string.camera_failed);
            }
        }
    }

    private void deletePhoto(@Nullable String photoPath) {
        if (photoPath != null) {
            new File(photoPath).delete();
        }
    }

    private void updateControls() {
        boolean enabled = ready
                && !sending
                && pendingPhotoPath == null;

        sendButton.setEnabled(enabled);
        cameraButton.setEnabled(enabled);
        messageInput.setEnabled(enabled);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(
                Constants.STATE_PHOTO_PATH,
                pendingPhotoPath
        );

        outState.putBoolean(
                Constants.EXTRA_OPEN_CAMERA,
                autoOpenCamera
        );

        super.onSaveInstanceState(outState);
    }

    private void closeWithError(@StringRes int message) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        showToast(message);
        finish();
    }

    private void openLogin() {
        sessionManager.clearSession();

        Intent intent = new Intent(this, LoginActivity.class);

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }

    private void showToast(@StringRes int message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
