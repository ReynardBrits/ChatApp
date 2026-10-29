package com.example.chatapp.activities;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import android.os.SystemClock;
import android.util.Log;

import com.example.chatapp.database.Message;
import com.example.chatapp.network.ApiException;
import com.example.chatapp.network.ApiMessage;
import com.example.chatapp.network.ChatSocketClient;
import com.example.chatapp.network.ConversationRepository;
import com.example.chatapp.utils.Constants;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.chatapp.network.ConversationsApi;

public final class ConversationViewModel extends ViewModel {

    private final ConversationsApi conversationsApi = new ConversationsApi();

    private final ExecutorService readWorker =
            Executors.newSingleThreadExecutor();

    // Main thread writes this; the read worker checks the latest value.
    private volatile long requestedReadId;

    // Accessed only by the read worker.
    private long confirmedReadId;
    private long attemptedReadId;

    private final ConversationRepository repository;
    private final LiveData<List<Message>> messages;

    private final MutableLiveData<UiState> state = new MutableLiveData<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ExecutorService workers =
            Executors.newFixedThreadPool(3);

    private ChatSocketClient socketClient;
    private long socketGeneration;

    private String draft = "";
    private String notice;

    private boolean active;
    private boolean sending;
    private boolean refreshing;
    private boolean refreshAgain;
    private boolean connected;
    private boolean sessionExpired;

    private volatile boolean cleared;

    public ConversationViewModel(ConversationRepository repository) {
        this.repository = repository;
        messages = repository.observeMessages();
        publishState();
    }

    public LiveData<List<Message>> getMessages() {
        return messages;
    }

    public LiveData<UiState> getState() {
        return state;
    }

    @MainThread
    public void setDraft(String text) {
        if (cleared) {
            return;
        }

        String updated = text == null ? "" : text;

        if (!draft.equals(updated)) {
            draft = updated;
            publishState();
        }
    }

    @MainThread
    public void clearNotice() {
        if (notice != null) {
            notice = null;
            publishState();
        }
    }

    @MainThread
    public void start() {
        if (active || !hasSession()) {
            return;
        }

        active = true;
        long generation = ++socketGeneration;

        socketClient = new ChatSocketClient(
                repository.getCurrentUser(),
                new ChatSocketClient.Listener() {
                    @Override
                    public void onReady() {
                        fromSocket(generation, () -> {
                            connected = true;
                            publishState();

                            // Recover messages missed while disconnected.
                            refresh();
                        });
                    }

                    @Override
                    public void onMessage(ApiMessage message) {
                        fromSocket(generation, () ->
                                cacheLiveMessage(message, generation)
                        );
                    }

                    @Override
                    public void onDisconnected(boolean unauthorized) {
                        fromSocket(generation, () -> {
                            connected = false;

                            if (unauthorized) {
                                expireSession();
                            } else {
                                publishState();
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        fromSocket(generation, () -> {
                            notice = message;
                            publishState();
                        });
                    }
                }
        );

        socketClient.connect();

        // HTTP history can load while the socket is connecting.
        refresh();
    }

    @MainThread
    public void stop() {
        active = false;
        connected = false;
        refreshAgain = false;
        socketGeneration++;

        if (socketClient != null) {
            socketClient.close();
            socketClient = null;
        }

        publishState();
    }

    @MainThread
    public void refresh() {
        if (!active || !hasSession()) {
            return;
        }

        if (refreshing) {
            refreshAgain = true;
            return;
        }

        queueReadUpdate(true);

        refreshing = true;
        publishState();

        workers.execute(() -> {
            if (cleared) {
                return;
            }

            Exception failure = null;

            try {
                repository.refreshHistory();
            } catch (IOException | RuntimeException exception) {
                failure = exception;
            }

            Exception result = failure;

            onMain(() -> {
                refreshing = false;

                boolean repeat = refreshAgain;
                refreshAgain = false;

                if (result == null) {
                    publishState();
                } else {
                    handleFailure(
                            result,
                            "Could not refresh messages. Tap the chat title to retry."
                    );
                }

                if (repeat) {
                    refresh();
                }
            });
        });
    }

    @MainThread
    public void sendText() {
        if (sending || !hasSession()) {
            return;
        }

        String submittedDraft = draft;
        String content = submittedDraft.trim();

        if (content.isEmpty()) {
            notice = "Enter a message.";
            publishState();
            return;
        }

        if (content.length() > Constants.MAX_MESSAGE_LENGTH) {
            notice = "Messages can contain up to 2000 characters.";
            publishState();
            return;
        }

        beginSend(
                () -> repository.sendText(content),
                submittedDraft,
                null
        );
    }

    // Once accepted, this ViewModel owns and deletes the original photo.
    @MainThread
    public boolean sendPhoto(File photo) {
        if (sending || !hasSession()) {
            return false;
        }

        if (photo == null || !photo.isFile() || photo.length() == 0) {
            notice = "The captured photo is unavailable.";
            publishState();
            return false;
        }

        beginSend(() -> repository.sendPhoto(photo), null, photo);
        return true;
    }

    private void beginSend(
            SendRequest request,
            @Nullable String submittedDraft,
            @Nullable File originalPhoto
    ) {
        long startedAt = SystemClock.elapsedRealtime();

        sending = true;
        notice = null;
        publishState();

        long queuedAt = SystemClock.elapsedRealtime();

        Log.d(
                "ChatTiming",
                "Initial UI update: " + (queuedAt - startedAt) + " ms"
        );

        workers.execute(() -> {
            Log.d(
                    "ChatTiming",
                    "Worker queue: "
                            + (SystemClock.elapsedRealtime() - queuedAt)
                            + " ms"
            );

            try {
                long requestStartedAt = SystemClock.elapsedRealtime();

                ApiMessage acknowledgement = request.execute();

                Log.d(
                        "ChatTiming",
                        "API request: "
                                + (SystemClock.elapsedRealtime() - requestStartedAt)
                                + " ms; message " + acknowledgement.getId()
                );

                long cacheStartedAt = SystemClock.elapsedRealtime();
                boolean cached;

                try {
                    cached = repository.cacheMessage(acknowledgement);
                } catch (IOException | RuntimeException exception) {
                    cached = false;
                    Log.e("ChatTiming", "Local cache failed", exception);
                }

                Log.d(
                        "ChatTiming",
                        "Room save: "
                                + (SystemClock.elapsedRealtime() - cacheStartedAt)
                                + " ms; cached=" + cached
                );

                boolean savedToCache = cached;
                long uiQueuedAt = SystemClock.elapsedRealtime();

                onMain(() -> {
                    Log.d(
                            "ChatTiming",
                            "UI callback queue: "
                                    + (SystemClock.elapsedRealtime() - uiQueuedAt)
                                    + " ms"
                    );

                    sending = false;

                    if (!hasSession()) {
                        return;
                    }

                    if (submittedDraft != null
                            && draft.equals(submittedDraft)) {
                        draft = "";
                    }

                    notice = savedToCache
                            ? null
                            : "Message sent. Refreshing the conversation display.";

                    publishState();

                    Log.d(
                            "ChatTiming",
                            "Send flow total: "
                                    + (SystemClock.elapsedRealtime() - startedAt)
                                    + " ms"
                    );

                    if (!savedToCache) {
                        refresh();
                    }
                });

            } catch (IOException | RuntimeException exception) {
                Log.e(
                        "ChatTiming",
                        "Send failed after "
                                + (SystemClock.elapsedRealtime() - startedAt)
                                + " ms",
                        exception
                );

                onMain(() -> {
                    sending = false;

                    handleFailure(
                            exception,
                            "Could not confirm sending. Check the conversation "
                                    + "before sending again."
                    );

                    refresh();
                });

            } finally {
                if (originalPhoto != null) {
                    originalPhoto.delete();
                }
            }
        });
    }

    private void cacheLiveMessage(ApiMessage message, long generation) {
        if (!repository.isForThisConversation(message)) {
            return;
        }

        workers.execute(() -> {
            if (cleared) {
                return;
            }

            try {
                repository.cacheMessage(message);

            } catch (IOException | RuntimeException exception) {
                fromSocket(generation, () ->
                        handleFailure(
                                exception,
                                "Could not display a live update. "
                                        + "Tap the chat title to refresh."
                        )
                );
            }
        });
    }

    @MainThread
    public void markReadThrough(long messageId) {
        if (!active || messageId <= requestedReadId || !hasSession()) {
            return;
        }

        requestedReadId = messageId;
        queueReadUpdate(false);
    }

    private void queueReadUpdate(boolean retry) {
        if (cleared || requestedReadId <= 0) {
            return;
        }

        readWorker.execute(() -> {
            long throughId = requestedReadId;

            if (throughId <= confirmedReadId
                    || (!retry && throughId <= attemptedReadId)) {
                return;
            }

            attemptedReadId = throughId;

            try {
                if (!repository.isCurrentSession()) {
                    throw new ApiException(401, "Please log in again.");
                }

                conversationsApi.markRead(
                        repository.getReceiver(),
                        throughId
                );

                confirmedReadId = throughId;
            } catch (IOException | RuntimeException exception) {
                onMain(() -> handleFailure(
                        exception,
                        "Could not update read status. "
                                + "Tap the chat title to retry."
                ));
            }
        });
    }

    private void handleFailure(Exception exception, String fallback) {
        if (cleared || sessionExpired) {
            return;
        }

        if ((exception instanceof ApiException
                && ((ApiException) exception).isUnauthorized())
                || !repository.isCurrentSession()) {
            expireSession();
            return;
        }

        String serverMessage = exception.getMessage();

        notice = exception instanceof ApiException
                && serverMessage != null
                && !serverMessage.trim().isEmpty()
                ? serverMessage
                : fallback;

        publishState();
    }

    private boolean hasSession() {
        if (cleared || sessionExpired) {
            return false;
        }

        if (!repository.isCurrentSession()) {
            expireSession();
            return false;
        }

        return true;
    }

    private void expireSession() {
        sessionExpired = true;
        stop();
    }

    private void fromSocket(long generation, Runnable action) {
        onMain(() -> {
            if (active && generation == socketGeneration) {
                action.run();
            }
        });
    }

    private void onMain(Runnable action) {
        mainHandler.post(() -> {
            if (!cleared) {
                action.run();
            }
        });
    }

    private void publishState() {
        if (!cleared) {
            state.setValue(new UiState(this));
        }
    }

    @Override
    protected void onCleared() {
        cleared = true;
        active = false;
        socketGeneration++;

        if (socketClient != null) {
            socketClient.close();
            socketClient = null;
        }

        // Accepted sends finish and clean up their temporary photos.
        workers.shutdown();
        readWorker.shutdown();

        mainHandler.removeCallbacksAndMessages(null);

        super.onCleared();
    }

    private interface SendRequest {
        ApiMessage execute() throws IOException;
    }

    public static final class UiState {

        public final boolean sending;
        public final boolean refreshing;
        public final boolean connected;
        public final boolean sessionExpired;
        public final String draft;

        @Nullable
        public final String notice;

        private UiState(ConversationViewModel source) {
            sending = source.sending;
            refreshing = source.refreshing;
            connected = source.connected;
            sessionExpired = source.sessionExpired;
            draft = source.draft;
            notice = source.notice;
        }
    }
}