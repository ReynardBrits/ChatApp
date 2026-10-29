(() => {
    "use strict";

    const app = window.ChatApp;
    if (app.page !== "conversations") return;

    app.ready.then(async account => {
        if (!account || app.isStopped()) return;

        const select = app.element("conversation-user");
        const input = app.element("message-input");
        const sendButton = app.element("send-message");
        const refreshButton = app.element("history-refresh");
        const form = app.element("message-form");
        const list = app.element("messages");
        const empty = app.element("messages-empty");
        const status = app.element("chat-status");
        const cameraLink = app.element("chat-camera-link");

        const messages = new Map();

        let users = new Set();
        let receiver = "";
        let sending = false;
        let loadingUsers = false;
        let loadingHistory = false;
        let historyFailed = false;
        let historyVersion = 0;
        let connectionStatus = "Connecting…";

        function updateControls() {
            if (app.isStopped()) return;

            const selected = users.has(receiver);

            select.disabled = sending || loadingUsers;
            input.disabled = !selected || sending;
            sendButton.disabled = !selected || sending;
            sendButton.textContent = sending ? "Sending…" : "Send";

            refreshButton.disabled =
                sending || loadingUsers || loadingHistory;

            cameraLink.href = selected
                ? "/camera?with=" + encodeURIComponent(receiver)
                : "/camera";

            status.textContent = loadingUsers ? "Loading users…"
                : loadingHistory ? "Loading conversation…"
                : connectionStatus;
        }

        function render() {
            if (app.isStopped()) return;

            const follow = list.childElementCount === 0
                || list.scrollHeight
                    - list.scrollTop - list.clientHeight < 80;

            const previousTop = list.scrollTop;
            const fragment = document.createDocumentFragment();

            [...messages.entries()]
                .sort((a, b) => a[0] - b[0])
                .forEach(([, node]) => fragment.append(node));

            list.replaceChildren(fragment);
            list.hidden = messages.size === 0;
            empty.hidden = messages.size > 0;

            empty.textContent = !receiver
                ? "Choose a user to view your conversation."
                : loadingHistory ? "Loading conversation…"
                : historyFailed
                    ? "Messages could not be loaded. Tap Refresh."
                    : "No messages yet.";

            list.scrollTop = follow ? list.scrollHeight : previousTop;
        }

        function checkMessage(message) {
            if (!message
                    || !Number.isSafeInteger(message.id)
                    || message.id <= 0
                    || !app.validName(message.sender)
                    || !app.validName(message.receiver)
                    || message.sender === message.receiver
                    || (message.sender !== account.username
                        && message.receiver !== account.username)) {
                throw new Error(
                    "The server returned an invalid conversation."
                );
            }
        }

        function belongs(message, other) {
            return (message.sender === account.username
                        && message.receiver === other)
                || (message.sender === other
                        && message.receiver === account.username);
        }

        function addMessage(message) {
            if (messages.has(message.id)) return false;

            messages.set(message.id, app.messageNode(message));
            return true;
        }

        async function loadHistory() {
            if (!receiver || app.isStopped()) return;

            const other = receiver;
            const version = ++historyVersion;
            const incoming = new Map();

            loadingHistory = true;
            historyFailed = false;

            updateControls();
            render();

            const current = () => !app.isStopped()
                && version === historyVersion
                && receiver === other;

            try {
                let beforeId = 0;

                while (current()) {
                    const query = "?with=" + encodeURIComponent(other)
                        + "&limit=100&beforeId=" + beforeId;

                    const page = await app.request(
                        "/api/messages" + query
                    );

                    if (!current()) return;

                    if (!Array.isArray(page) || page.length > 100) {
                        throw new Error(
                            "The conversation history is invalid."
                        );
                    }

                    let previousId = 0;

                    for (const message of page) {
                        checkMessage(message);

                        if (!belongs(message, other)
                                || message.id <= previousId
                                || (beforeId > 0
                                    && message.id >= beforeId)) {
                            throw new Error(
                                "The conversation history is invalid."
                            );
                        }

                        previousId = message.id;

                        if (!messages.has(message.id)) {
                            incoming.set(
                                message.id,
                                app.messageNode(message)
                            );
                        }
                    }

                    if (page.length < 100) break;

                    beforeId = page[0].id;
                }

                if (!current()) return;

                incoming.forEach((node, id) => {
                    if (!messages.has(id)) {
                        messages.set(id, node);
                    }
                });

            } catch (error) {
                if (current()) {
                    historyFailed = true;
                    app.notify(error.message, true);
                }

            } finally {
                if (current()) {
                    loadingHistory = false;
                    updateControls();
                    render();
                }
            }
        }

        function selectConversation() {
            const next = users.has(select.value) ? select.value : "";

            if (receiver !== next) {
                historyVersion++;
                receiver = next;

                messages.clear();
                input.value = "";
                loadingHistory = false;
                historyFailed = false;
            }

            history.replaceState(
                null,
                "",
                receiver
                    ? "/conversations?with="
                        + encodeURIComponent(receiver)
                    : "/conversations"
            );

            updateControls();
            render();

            if (receiver) loadHistory();
        }

        async function reloadUsers() {
            if (loadingUsers || sending || app.isStopped()) return;

            loadingUsers = true;
            updateControls();

            try {
                const accounts = await app.loadUsers(select);

                if (app.isStopped()) return;

                users = new Set(
                    accounts.map(user => user.username)
                );

                selectConversation();

            } catch (error) {
                app.notify(
                    error.message + " Tap Refresh to retry.",
                    true
                );

            } finally {
                loadingUsers = false;
                updateControls();
            }
        }

        select.addEventListener("change", selectConversation);

        refreshButton.addEventListener("click", () => {
            app.notify();
            reloadUsers();
        });

        form.addEventListener("submit", async event => {
            event.preventDefault();

            if (sending || loadingUsers || !users.has(receiver)) return;

            const submitted = input.value;
            const content = submitted.trim();

            if (!content || content.length > 2000) {
                app.notify(
                    "Enter a message of 1 to 2000 characters.",
                    true
                );
                return;
            }

            const other = receiver;

            sending = true;
            app.notify();
            updateControls();

            try {
                const message = await app.request(
                    "/api/messages",
                    app.post({ receiver: other, content })
                );

                if (app.isStopped()) return;

                checkMessage(message);

                if (message.sender !== account.username
                        || !belongs(message, other)) {
                    throw new Error(
                        "An unexpected send response was received."
                    );
                }

                if (receiver === other && addMessage(message)) {
                    render();
                }

                if (input.value === submitted) {
                    input.value = "";
                }

            } catch (error) {
                app.notify(
                    "Could not confirm sending. " + error.message
                        + " Check the conversation before sending again.",
                    true
                );

                await loadHistory();

            } finally {
                sending = false;
                updateControls();
            }
        });

        app.onCleanup(() => {
            historyVersion++;
        });

        app.openSocket("/ws/chat", {
            onState(value) {
                connectionStatus = value;
                updateControls();
            },

            onReady: loadHistory,

            onMessage(update) {
                if (update.type !== "message") return;

                const message = update.message;
                checkMessage(message);

                if (belongs(message, receiver) && addMessage(message)) {
                    render();
                }
            }
        });

        await reloadUsers();

    }).catch(error => {
        window.ChatApp.notify(error.message, true);
    });
})();