package com.example.chatapp.activities;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.chatapp.database.ChatDatabaseHelper;
import com.example.chatapp.database.User;
import com.example.chatapp.network.ApiAccount;
import com.example.chatapp.network.ApiConversation;
import com.example.chatapp.network.ApiException;
import com.example.chatapp.network.ApiMessage;
import com.example.chatapp.network.ChatSocketClient;
import com.example.chatapp.network.ConversationsApi;
import com.example.chatapp.network.UsersApi;
import com.example.chatapp.utils.SessionManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HomeViewModel extends AndroidViewModel {

    private final SessionManager sessionManager;
    private final ApiAccount currentAccount;
    private final ChatDatabaseHelper database;

    private final ConversationsApi conversationsApi = new ConversationsApi();
    private final UsersApi usersApi = new UsersApi();

    private final MutableLiveData<UiState> state = new MutableLiveData<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private List<ApiConversation> conversations = Collections.emptyList();
    private List<ApiAccount> users = Collections.emptyList();

    private ChatSocketClient socketClient;
    private long socketGeneration;

    private boolean active;
    private boolean refreshing;
    private boolean refreshAgain;
    private boolean sessionExpired;

    @Nullable
    private String error;

    private volatile boolean cleared;

    public HomeViewModel(@NonNull Application application) {
        super(application);

        sessionManager = new SessionManager(application);

        if (!sessionManager.isLoggedIn()) {
            throw new IllegalStateException("Log in before opening Home.");
        }

        currentAccount = new ApiAccount(
                sessionManager.getUserId(),
                sessionManager.getUsername()
        );

        database = ChatDatabaseHelper.getInstance(application);
        publishState();
    }

    public ApiAccount getCurrentAccount() {
        return currentAccount;
    }

    public LiveData<UiState> getState() {
        return state;
    }

    @MainThread
    public void start() {
        if (active || !hasSession()) {
            return;
        }

        active = true;
        long generation = ++socketGeneration;

        socketClient = new ChatSocketClient(
                currentAccount.getUsername(),
                new ChatSocketClient.Listener() {
                    @Override
                    public void onReady() {
                        fromSocket(generation, () -> refresh());
                    }

                    @Override
                    public void onMessage(ApiMessage message) {
                        fromSocket(generation, () -> refresh());
                    }

                    @Override
                    public void onDisconnected(boolean unauthorized) {
                        fromSocket(generation, () -> {
                            if (unauthorized) {
                                expireSession();
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        fromSocket(generation, () -> {
                            error = message;
                            publishState();
                        });
                    }
                }
        );

        socketClient.connect();
        refresh();
    }

    @MainThread
    public void stop() {
        active = false;
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

        // Several live updates need at most one follow-up refresh.
        if (refreshing) {
            refreshAgain = true;
            return;
        }

        refreshing = true;
        error = null;
        publishState();

        long generation = socketGeneration;

        worker.execute(() -> {
            if (cleared) {
                return;
            }

            List<ApiAccount> loadedUsers = Collections.emptyList();
            List<ApiConversation> loadedConversations = Collections.emptyList();
            Exception failure = null;

            try {
                requireSession();
                loadedUsers = usersApi.getOtherUsers();

                requireSession();
                loadedConversations = conversationsApi.getConversations(
                        currentAccount.getUsername()
                );

                cacheAccounts(loadedUsers, loadedConversations);
                requireSession();
            } catch (IOException | RuntimeException exception) {
                failure = exception;
            }

            List<ApiAccount> usersResult = loadedUsers;
            List<ApiConversation> conversationsResult = loadedConversations;
            Exception failureResult = failure;

            mainHandler.post(() -> {
                if (cleared) {
                    return;
                }

                refreshing = false;
                boolean repeat = refreshAgain;
                refreshAgain = false;

                // Ignore a response from an earlier visit to Home.
                if (!active || generation != socketGeneration) {
                    publishState();

                    if (active) {
                        refresh();
                    }

                    return;
                }

                if (!hasSession()) {
                    return;
                }

                if (failureResult instanceof ApiException
                        && ((ApiException) failureResult).isUnauthorized()) {
                    expireSession();
                    return;
                }

                if (failureResult == null) {
                    users = usersResult;
                    conversations = conversationsResult;
                    error = null;
                } else {
                    String message = failureResult.getMessage();

                    error = failureResult instanceof ApiException
                            && message != null
                            && !message.trim().isEmpty()
                            ? message
                            : "Could not load chats. Tap Refresh to try again.";
                }

                publishState();

                if (repeat) {
                    refresh();
                }
            });
        });
    }

    private void cacheAccounts(
            List<ApiAccount> accounts,
            List<ApiConversation> summaries
    ) throws ApiException {
        requireSession();

        List<User> cachedUsers = new ArrayList<>();
        cachedUsers.add(toCachedUser(currentAccount));

        for (ApiAccount account : accounts) {
            cachedUsers.add(toCachedUser(account));
        }

        // Include peers who registered after the user list was fetched.
        for (ApiConversation conversation : summaries) {
            cachedUsers.add(toCachedUser(conversation.getUser()));
        }

        database.userDao().upsertUsers(cachedUsers);
    }

    private static User toCachedUser(ApiAccount account) {
        User user = new User();
        user.setId(account.getId());
        user.setUsername(account.getUsername());
        return user;
    }

    private boolean isCurrentSession() {
        return sessionManager.isLoggedIn()
                && sessionManager.getUserId() == currentAccount.getId()
                && currentAccount.getUsername().equals(
                sessionManager.getUsername()
        );
    }

    private void requireSession() throws ApiException {
        if (cleared || !isCurrentSession()) {
            throw new ApiException(401, "Please log in again.");
        }
    }

    private boolean hasSession() {
        if (cleared || sessionExpired) {
            return false;
        }

        if (!isCurrentSession()) {
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
        mainHandler.post(() -> {
            if (!cleared && active && generation == socketGeneration) {
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

        worker.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        super.onCleared();
    }

    public static final class UiState {

        public final List<ApiConversation> conversations;
        public final List<ApiAccount> users;
        public final boolean refreshing;
        public final boolean sessionExpired;

        @Nullable
        public final String error;

        private UiState(HomeViewModel source) {
            conversations = source.conversations;
            users = source.users;
            refreshing = source.refreshing;
            sessionExpired = source.sessionExpired;
            error = source.error;
        }
    }
}
