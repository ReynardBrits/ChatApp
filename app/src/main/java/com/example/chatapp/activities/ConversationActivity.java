package com.example.chatapp.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatapp.R;
import com.example.chatapp.adapters.MessageAdapter;
import com.example.chatapp.database.Message;
import com.example.chatapp.network.ConversationRepository;
import com.example.chatapp.utils.Constants;
import com.example.chatapp.utils.SessionManager;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ConversationActivity extends BaseActivity {

    private SessionManager sessionManager;
    private ConversationViewModel viewModel;
    private ConversationViewModel.UiState uiState;

    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private MessageAdapter adapter;

    private TextView title;
    private TextView emptyText;
    private EditText messageInput;
    private MaterialButton sendButton;
    private MaterialButton cameraButton;

    private String currentUser;
    private int currentUserId;
    private String receiver;
    private String pendingPhotoPath;

    private boolean autoOpenCamera;
    private boolean bindingDraft;
    private boolean messagesLoaded;
    private boolean leaving;

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.TakePicture(),
                    this::handlePhotoResult
            );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        sessionManager = new SessionManager(this);
        currentUser = sessionManager.getUsername();
        currentUserId = sessionManager.getUserId();

        if (!sessionManager.isLoggedIn()) {
            openLogin();
            return;
        }

        String requestedReceiver = getIntent().getStringExtra(
                Constants.EXTRA_RECEIVER
        );

        receiver = requestedReceiver == null
                ? ""
                : requestedReceiver.trim().toLowerCase(Locale.ROOT);

        if (!receiver.matches("[a-z0-9_]{3,24}")
                || receiver.equals(currentUser)) {
            showToast(getString(R.string.invalid_receiver));
            finish();
            return;
        }

        setScreenLayout(R.layout.activity_conversation);

        title = findViewById(R.id.chat_title);
        emptyText = findViewById(R.id.empty_conversation);
        recyclerView = findViewById(R.id.recyclerView);
        messageInput = findViewById(R.id.message);
        sendButton = findViewById(R.id.btn_send);
        cameraButton = findViewById(R.id.btn_camera);

        // The ViewModel owns the draft during screen rotation.
        messageInput.setSaveEnabled(false);

        adapter = new MessageAdapter(currentUser);
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        title.setMaxLines(4);
        title.setFocusable(true);
        title.setTooltipText("Tap to refresh messages");

        try {
            ViewModelProvider.Factory factory =
                    new ViewModelProvider.Factory() {
                        @NonNull
                        @Override
                        public <T extends ViewModel> T create(
                                @NonNull Class<T> modelClass,
                                @NonNull CreationExtras extras
                        ) {
                            if (!modelClass.isAssignableFrom(
                                    ConversationViewModel.class
                            )) {
                                throw new IllegalArgumentException(
                                        "Unknown ViewModel class."
                                );
                            }

                            ConversationRepository repository =
                                    new ConversationRepository(
                                            getApplicationContext(),
                                            receiver
                                    );

                            return modelClass.cast(
                                    new ConversationViewModel(repository)
                            );
                        }
                    };

            viewModel = new ViewModelProvider(this, factory)
                    .get(ConversationViewModel.class);

        } catch (RuntimeException exception) {
            if (!sessionManager.isLoggedIn()) {
                openLogin();
            } else {
                showToast(getString(R.string.conversation_failed));
                finish();
            }

            return;
        }

        uiState = viewModel.getState().getValue();

        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence text,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence text,
                    int start,
                    int before,
                    int count
            ) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                if (!bindingDraft && !leaving) {
                    viewModel.setDraft(text.toString());
                }
            }
        });

        findViewById(R.id.btn_back_home).setOnClickListener(
                view -> finish()
        );

        sendButton.setOnClickListener(view -> {
            if (checkSession()) {
                viewModel.sendText();
            }
        });

        cameraButton.setOnClickListener(view -> openCamera());

        title.setOnClickListener(view -> {
            if (checkSession()) {
                viewModel.refresh();
            }
        });

        viewModel.getMessages().observe(this, this::displayMessages);

        viewModel.getState().observe(this, state -> {
            uiState = state;
            render();
        });

        render();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (viewModel != null && canUpdateUi() && checkSession()) {
            viewModel.start();
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        if (autoOpenCamera
                && pendingPhotoPath == null
                && viewModel != null
                && canUpdateUi()) {
            autoOpenCamera = false;
            openCamera();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (viewModel != null) {
            viewModel.stop();
        }
    }

    private void displayMessages(@Nullable List<Message> messages) {
        if (!canUpdateUi()) {
            return;
        }

        int previousCount = adapter.getItemCount();
        int lastVisible = layoutManager.findLastVisibleItemPosition();

        boolean followLatest = previousCount == 0
                || lastVisible == RecyclerView.NO_POSITION
                || lastVisible >= previousCount - 2;

        adapter.setMessages(
                messages == null ? Collections.emptyList() : messages
        );

        messagesLoaded = true;
        render();

        int count = adapter.getItemCount();

        if (followLatest && count > 0) {
            recyclerView.scrollToPosition(count - 1);
        }
    }

    private void render() {
        if (!canUpdateUi() || uiState == null) {
            return;
        }

        if (uiState.sessionExpired) {
            openLogin();
            return;
        }

        if (!messageInput.getText().toString().equals(uiState.draft)) {
            bindingDraft = true;

            try {
                messageInput.setText(uiState.draft);
                messageInput.setSelection(messageInput.length());
            } finally {
                bindingDraft = false;
            }
        }

        boolean enabled = !uiState.sending
                && pendingPhotoPath == null;

        sendButton.setEnabled(enabled);
        cameraButton.setEnabled(enabled);
        messageInput.setEnabled(enabled);

        sendButton.setText(
                uiState.sending
                        ? "Sending…"
                        : getString(R.string.send)
        );

        String status;

        if (pendingPhotoPath != null) {
            status = "Taking photo…";
        } else if (uiState.sending) {
            status = "Sending…";
        } else if (uiState.refreshing) {
            status = "Loading messages…";
        } else if (uiState.connected) {
            status = "Connected · Tap to refresh";
        } else {
            status = "Connecting · Tap to refresh";
        }

        title.setText(
                getString(R.string.chat_with, receiver)
                        + "\n"
                        + status
        );

        boolean empty = adapter.getItemCount() == 0;

        emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);

        if (empty) {
            emptyText.setText(
                    !messagesLoaded || uiState.refreshing
                            ? "Loading messages…"
                            : getString(R.string.empty_conversation)
            );
        }

        if (uiState.notice != null) {
            showToast(uiState.notice);
            viewModel.clearNotice();
        }
    }

    private void openCamera() {
        if (!canUpdateUi()
                || viewModel == null
                || uiState == null
                || uiState.sending
                || pendingPhotoPath != null
                || !checkSession()) {
            return;
        }

        try {
            File directory = new File(
                    getFilesDir(),
                    Constants.IMAGE_DIRECTORY
            );

            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Unable to create photo directory.");
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

            render();
            cameraLauncher.launch(photoUri);

        } catch (IOException | RuntimeException exception) {
            deletePhoto(pendingPhotoPath);
            pendingPhotoPath = null;
            render();

            showToast(getString(
                    exception instanceof ActivityNotFoundException
                            ? R.string.no_camera
                            : R.string.camera_failed
            ));
        }
    }

    private void handlePhotoResult(boolean saved) {
        String photoPath = pendingPhotoPath;
        pendingPhotoPath = null;

        if (photoPath == null) {
            return;
        }

        File photo = new File(photoPath);

        if (!canUpdateUi() || viewModel == null) {
            photo.delete();
            return;
        }

        if (!checkSession()) {
            photo.delete();
            return;
        }

        if (saved && photo.isFile() && photo.length() > 0) {
            // The ViewModel takes ownership only when it accepts the photo.
            if (!viewModel.sendPhoto(photo)) {
                photo.delete();
            }
        } else {
            photo.delete();

            if (saved) {
                showToast(getString(R.string.camera_failed));
            }
        }

        render();
    }

    private boolean checkSession() {
        if (!canUpdateUi()) {
            return false;
        }

        if (sessionManager.getUserId() != currentUserId
                || !currentUser.equals(sessionManager.getUsername())) {
            finish();
            return false;
        }

        if (!sessionManager.isLoggedIn()) {
            openLogin();
            return false;
        }

        return true;
    }

    private void openLogin() {
        if (leaving) {
            return;
        }

        leaving = true;

        // An older screen must not clear a different account's session.
        if (sessionManager.getUserId() > 0
                && (sessionManager.getUserId() != currentUserId
                || !sessionManager.getUsername().equals(currentUser))) {
            finish();
            return;
        }

        if (viewModel != null) {
            viewModel.stop();
        }

        deletePhoto(pendingPhotoPath);
        pendingPhotoPath = null;

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

    private void deletePhoto(@Nullable String path) {
        if (path != null) {
            new File(path).delete();
        }
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

    @Override
    protected void onDestroy() {
        if (isFinishing()) {
            deletePhoto(pendingPhotoPath);
            pendingPhotoPath = null;
        }

        super.onDestroy();
    }
}
