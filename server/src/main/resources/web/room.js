(() => {
    "use strict";

    const app = window.ChatApp;
    if (app.page !== "room") return;

    app.ready.then(account => {
        if (!account || app.isStopped()) return;

        const form = app.element("room-form");
        const input = app.element("room-input");
        const sendButton = app.element("room-send");
        const status = app.element("room-status");
        const list = app.element("room-messages");
        const empty = app.element("room-empty");

        const MAX_MESSAGES = 300;
        const CONFIRMATION_TIMEOUT = 30000;
        const clientIdPattern =
            /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

        const displayed = new Map();

        let socket = null;
        let connected = false;
        let pending = null;
        let confirmationTimer = null;

        function updateControls() {
            const available =
                connected && socket?.ready && !app.isStopped();

            input.disabled = !available || pending !== null;
            sendButton.disabled = !available || pending !== null;
            sendButton.textContent = pending ? "Sending…" : "Send";
        }

        function clearPending() {
            if (confirmationTimer !== null) {
                clearTimeout(confirmationTimer);
                confirmationTimer = null;
            }

            pending = null;
            updateControls();
        }

        function warnUnconfirmed(reason) {
            if (!pending) return;

            clearPending();

            if (!app.isStopped()) {
                app.notify(
                    reason
                    + " Your text has been kept. Check the room before sending again.",
                    true
                );
            }
        }

        function receiveMessage(update) {
            if (app.isStopped() || update.type !== "room_message") return;

            try {
                const message = update.message;

                if (!message
                        || !app.validName(message.sender)
                        || typeof message.clientId !== "string"
                        || !clientIdPattern.test(message.clientId)
                        || message.imageName != null
                        || message.receiver != null) {
                    throw new Error("Invalid room message.");
                }

                const key = message.sender + ":" + message.clientId;
                const node = app.messageNode(message);

                if (!displayed.has(key)) {
                    const nearBottom =
                        list.scrollHeight
                        - list.scrollTop
                        - list.clientHeight < 80;

                    displayed.set(key, node);
                    list.appendChild(node);

                    if (displayed.size > MAX_MESSAGES) {
                        const oldestKey = displayed.keys().next().value;
                        displayed.get(oldestKey).remove();
                        displayed.delete(oldestKey);
                    }

                    list.hidden = false;
                    empty.hidden = true;

                    if (nearBottom || displayed.size === 1) {
                        list.scrollTop = list.scrollHeight;
                    }
                }

                if (pending
                        && message.sender === account.username
                        && message.clientId === pending.clientId
                        && message.content === pending.content) {
                    if (input.value === pending.draft) {
                        input.value = "";
                    }

                    clearPending();
                }
            } catch {
                app.notify(
                    "An invalid live room message was received.",
                    true
                );
            }
        }

        form.addEventListener("submit", event => {
            event.preventDefault();

            if (app.isStopped() || pending) return;

            const draft = input.value;
            const content = draft.trim();

            if (!content || content.length > 2000) {
                app.notify(
                    "Enter a message between 1 and 2000 characters.",
                    true
                );
                return;
            }

            if (!connected || !socket?.ready) {
                app.notify("Wait until the live room reconnects.", true);
                return;
            }

            try {
                if (!window.crypto?.randomUUID) {
                    throw new Error(
                        "Open ChatApp through localhost to use the live room."
                    );
                }

                const clientId = window.crypto.randomUUID();

                pending = { clientId, content, draft };
                app.notify("");
                updateControls();

                confirmationTimer = setTimeout(() => {
                    warnUnconfirmed(
                        "The server has not confirmed your message."
                    );
                }, CONFIRMATION_TIMEOUT);

                socket.send({
                    type: "message",
                    clientId,
                    content
                });
            } catch (error) {
                clearPending();

                app.notify(
                    "Could not send the room message: " + error.message,
                    true
                );
            }
        });

        list.hidden = true;
        empty.hidden = false;
        status.textContent = "Connecting to the live room…";
        updateControls();

        socket = app.openSocket("/ws/room", {
            onState(text) {
                if (app.isStopped()) return;

                status.textContent = text;
                updateControls();
            },

            onReady() {
                connected = true;
                updateControls();
            },

            onMessage: receiveMessage,

            onError() {
                clearPending();
            },

            onDisconnect() {
                connected = false;

                warnUnconfirmed(
                    "The connection closed before confirmation."
                );

                updateControls();
            }
        });

        app.onCleanup(() => {
            connected = false;
            clearPending();
            displayed.clear();
        });

        updateControls();
    }).catch(error => app.notify(error.message, true));
})();