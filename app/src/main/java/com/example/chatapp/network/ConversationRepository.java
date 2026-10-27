package com.example.chatapp.network;

import android.content.Context;

import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.example.chatapp.database.ChatDatabaseHelper;
import com.example.chatapp.database.Message;
import com.example.chatapp.database.User;
import com.example.chatapp.utils.PhotoPreparer;
import com.example.chatapp.utils.SessionManager;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ConversationRepository {

    private static final int PAGE_SIZE = 100;

    private final Context appContext;
    private final SessionManager sessionManager;
    private final ChatDatabaseHelper database;

    private final MessagesApi messagesApi = new MessagesApi();
    private final PhotosApi photosApi = new PhotosApi();

    private final int currentUserId;
    private final String currentUser;
    private final String receiver;

    public ConversationRepository(Context context, String receiver) {
        appContext = context.getApplicationContext();
        sessionManager = new SessionManager(appContext);

        if (!sessionManager.isLoggedIn()) {
            throw new IllegalStateException("Log in before opening a conversation.");
        }

        currentUserId = sessionManager.getUserId();
        currentUser = sessionManager.getUsername();

        String normalizedReceiver = receiver == null
                ? ""
                : receiver.trim().toLowerCase(Locale.ROOT);

        if (!normalizedReceiver.matches("[a-z0-9_]{3,24}")
                || normalizedReceiver.equals(currentUser)) {
            throw new IllegalArgumentException("Invalid recipient.");
        }

        this.receiver = normalizedReceiver;
        database = ChatDatabaseHelper.getInstance(appContext);
    }

    public LiveData<List<Message>> observeMessages() {
        return database.messageDao()
                .getConversation(currentUser, receiver);
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public String getReceiver() {
        return receiver;
    }

    public boolean isCurrentSession() {
        return sessionManager.isLoggedIn()
                && sessionManager.getUserId() == currentUserId
                && currentUser.equals(sessionManager.getUsername());
    }

    public boolean isForThisConversation(ApiMessage message) {
        if (message == null) {
            return false;
        }

        return (currentUser.equals(message.getSender())
                && receiver.equals(message.getReceiver()))
                || (receiver.equals(message.getSender())
                && currentUser.equals(message.getReceiver()));
    }

    // Downloads every history page, including messages older than the first 100.
    @WorkerThread
    public void refreshHistory() throws IOException {
        verifyParticipants();

        long beforeId = 0;

        while (true) {
            requireSession();

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("History loading stopped.");
            }

            List<ApiMessage> page = messagesApi.getConversation(
                    receiver,
                    beforeId,
                    PAGE_SIZE
            );

            requireSession();

            if (page.isEmpty()) {
                return;
            }

            if (page.size() > PAGE_SIZE) {
                throw new IOException("The server returned an invalid history page.");
            }

            List<Message> cachedMessages = new ArrayList<>(page.size());
            long previousId = 0;

            for (ApiMessage message : page) {
                if (!isForThisConversation(message)
                        || message.getId() <= previousId
                        || (beforeId > 0 && message.getId() >= beforeId)) {
                    throw new IOException("The server returned invalid history.");
                }

                cachedMessages.add(toEntity(message));
                previousId = message.getId();
            }

            database.messageDao().upsertMessages(cachedMessages);

            if (page.size() < PAGE_SIZE) {
                return;
            }

            beforeId = page.get(0).getId();
        }
    }

    // Returns the server acknowledgement. The caller then caches that result.
    @WorkerThread
    public ApiMessage sendText(String content) throws IOException {
        requireSession();

        ApiMessage message = messagesApi.sendText(receiver, content);

        verifySentMessage(message);
        return message;
    }

    // Deletes the temporary upload copy; the caller owns the original photo.
    @WorkerThread
    public ApiMessage sendPhoto(File originalPhoto) throws IOException {
        requireSession();

        File preparedPhoto = PhotoPreparer.prepare(appContext, originalPhoto);

        try {
            requireSession();

            ApiMessage message = photosApi.sendPhoto(
                    receiver,
                    preparedPhoto
            );

            verifySentMessage(message);
            return message;

        } finally {
            preparedPhoto.delete();
        }
    }

    // Used for both HTTP acknowledgements and incoming WebSocket messages.
    @WorkerThread
    public boolean cacheMessage(ApiMessage message) throws IOException {
        requireSession();

        if (!isForThisConversation(message)) {
            return false;
        }

        verifyParticipants();
        database.messageDao().upsertMessage(toEntity(message));
        return true;
    }

    private Message toEntity(ApiMessage source) {
        Message message = new Message();

        message.setId(source.getId());
        message.setSender(source.getSender());
        message.setReceiver(source.getReceiver());
        message.setContent(source.getContent());
        message.setTimestamp(source.getTimestamp());

        message.setImagePath(
                source.hasPhoto()
                        ? ServerConfig.BASE_URL
                        + "api/photos/"
                        + source.getImageName()
                        : null
        );

        return message;
    }

    private void verifySentMessage(ApiMessage message) throws IOException {
        requireSession();

        if (message == null
                || !currentUser.equals(message.getSender())
                || !receiver.equals(message.getReceiver())) {
            throw new IOException("The server returned an invalid sent message.");
        }
    }

    @WorkerThread
    private void verifyParticipants() throws IOException {
        requireSession();

        User sender = database.userDao()
                .getUserByUsername(currentUser);

        User recipient = database.userDao()
                .getUserByUsername(receiver);

        if (sender == null
                || sender.getId() != currentUserId
                || recipient == null
                || recipient.getId() == currentUserId) {
            throw new IOException(
                    "Return to Home and refresh the account list."
            );
        }
    }

    private void requireSession() throws ApiException {
        if (!isCurrentSession()) {
            throw new ApiException(401, "Please log in again.");
        }
    }
}