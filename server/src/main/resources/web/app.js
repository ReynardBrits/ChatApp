(() => {
    "use strict";

    const element = id => document.getElementById(id);
    const page = location.pathname.replace(/^\/|\/$/g, "") || "home";
    const names = /^[a-z0-9_]{3,24}$/;
    const photoNames = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png)$/;

    const cleanups = [];
    const requests = new Set();

    let account = null;
    let stopped = false;

    function notify(message = "", error = false) {
        if (stopped) return;

        const status = element("page-status");
        status.textContent = message;
        status.hidden = !message;
        status.classList.toggle("error", error);
    }

    function cleanup() {
        if (stopped) return;

        stopped = true;
        requests.forEach(controller => controller.abort());

        cleanups.forEach(task => {
            try {
                task();
            } catch (_) {
                // Continue cleaning up the other resources.
            }
        });
    }

    function onCleanup(task) {
        if (stopped) task();
        else cleanups.push(task);
    }

    function expireSession() {
        cleanup();
        location.replace("/login?expired=1");
    }

    async function request(path, options = {}, requiresLogin = true) {
        if (stopped) throw new Error("The page is closing.");

        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 45000);

        requests.add(controller);

        try {
            const response = await fetch(path, {
                ...options,
                credentials: "same-origin",
                redirect: "error",
                cache: "no-store",
                signal: controller.signal
            });

            const data = await response.json().catch(() => null);

            if (response.status === 401 && requiresLogin) {
                expireSession();
                throw new Error("Please log in again.");
            }

            if (!response.ok) {
                throw new Error(
                    typeof data?.error === "string"
                        ? data.error
                        : "Request failed (HTTP " + response.status + ")."
                );
            }

            if (data === null) {
                throw new Error("The server returned an invalid response.");
            }

            return data;

        } catch (error) {
            if (error.name === "AbortError") {
                throw new Error("The server request did not complete.");
            }

            throw error;

        } finally {
            clearTimeout(timer);
            requests.delete(controller);
        }
    }

    function post(data) {
        return {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data)
        };
    }

    function validAccount(value) {
        return value
            && Number.isSafeInteger(value.id)
            && value.id > 0
            && typeof value.username === "string"
            && names.test(value.username);
    }

    async function loadUsers(select) {
        const users = await request("/api/users");

        if (!Array.isArray(users)
                || users.some(user =>
                    !validAccount(user)
                    || user.id === account.id
                    || user.username === account.username
                )) {
            throw new Error("The server returned an invalid user list.");
        }

        const unique = new Set(users.map(user => user.username));

        if (unique.size !== users.length) {
            throw new Error("The server returned duplicate users.");
        }

        select.replaceChildren(new Option(
            users.length ? "Choose a user" : "No other registered users",
            ""
        ));

        users.forEach(user => {
            select.add(new Option(user.username, user.username));
        });

        const selected = new URLSearchParams(location.search).get("with");

        if (unique.has(selected)) {
            select.value = selected;
        }

        return users;
    }

    function messageNode(message) {
        const hasPhoto = message?.imageName != null;

        if (!message
                || typeof message.sender !== "string"
                || !names.test(message.sender)
                || typeof message.content !== "string"
                || message.content.length > 2000
                || (!message.content.trim() && !hasPhoto)
                || (hasPhoto && !photoNames.test(message.imageName))
                || !Number.isSafeInteger(message.timestamp)
                || message.timestamp <= 0) {
            throw new Error("The server returned an invalid message.");
        }

        const date = new Date(message.timestamp);

        if (!Number.isFinite(date.getTime())) {
            throw new Error("The message timestamp is invalid.");
        }

        const node = element("message-template").content
            .firstElementChild.cloneNode(true);

        node.classList.toggle(
            "own",
            message.sender === account.username
        );

        node.querySelector(".message-author").textContent =
            message.sender === account.username ? "You" : message.sender;

        const time = node.querySelector(".message-time");

        time.dateTime = date.toISOString();
        time.textContent = date.toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit"
        });
        time.title = date.toLocaleString();

        const content = node.querySelector(".message-content");

        content.textContent = message.content;
        content.hidden = !message.content;

        const image = node.querySelector(".message-image");

        if (hasPhoto) {
            image.src = "/api/photos/"
                + encodeURIComponent(message.imageName);

            image.alt = "Photo from " + message.sender;
            image.hidden = false;
        }

        return node;
    }

    function openSocket(path, handlers = {}) {
        if (path !== "/ws/chat" && path !== "/ws/room") {
            throw new Error("Unknown live connection.");
        }

        let socket = null;
        let ready = false;
        let closed = false;
        let attempts = 0;
        let retryTimer;
        let readyTimer;
        let heartbeat;

        function clearTimers() {
            clearTimeout(retryTimer);
            clearTimeout(readyTimer);
            clearInterval(heartbeat);
        }

        function send(event) {
            if (closed || stopped || !ready
                    || socket?.readyState !== WebSocket.OPEN) {
                throw new Error("The live connection is unavailable.");
            }

            const text = JSON.stringify(event);

            if (text.length > 16384 || socket.bufferedAmount > 65536) {
                throw new Error("The connection is busy. Try again shortly.");
            }

            socket.send(text);
        }

        function close() {
            if (closed) return;

            closed = true;
            ready = false;
            clearTimers();

            const active = socket;
            socket = null;

            active?.close(1000, "Page closed");
        }

        function connect() {
            if (closed || stopped) return;

            handlers.onState?.("Connecting…");

            const url = new URL(path, location.origin);
            url.protocol = location.protocol === "https:" ? "wss:" : "ws:";

            const active = new WebSocket(url);

            socket = active;
            ready = false;

            readyTimer = setTimeout(() => {
                if (socket === active && !ready) {
                    active.close();
                }
            }, 15000);

            active.onmessage = event => {
                if (closed || stopped || socket !== active) return;

                try {
                    if (typeof event.data !== "string"
                            || event.data.length > 16384) {
                        throw new Error("Invalid live update.");
                    }

                    const update = JSON.parse(event.data);

                    if (update?.type === "ready") {
                        if (update.username !== account.username) {
                            expireSession();
                            return;
                        }

                        if (ready) return;

                        ready = true;
                        attempts = 0;

                        clearTimeout(readyTimer);

                        heartbeat = setInterval(() => {
                            try {
                                send({ type: "ping" });
                            } catch (_) {
                                active.close();
                            }
                        }, 30000);

                        handlers.onState?.("Connected");
                        handlers.onReady?.();

                    } else if (update?.type === "pong") {
                        return;

                    } else if (update?.type === "error") {
                        const message = typeof update.error === "string"
                            ? update.error
                            : "A live request failed.";

                        handlers.onError?.(message);
                        notify(message, true);

                    } else if (update?.type === "message"
                            || update?.type === "room_message") {
                        if (ready) {
                            handlers.onMessage?.(update);
                        }

                    } else {
                        throw new Error("Invalid live update.");
                    }

                } catch (_) {
                    notify("A live update could not be processed.", true);
                    active.close(1002, "Invalid update");
                }
            };

            active.onerror = () => {
                if (!closed && !stopped && socket === active) {
                    handlers.onState?.("Connection interrupted");
                }
            };

            active.onclose = event => {
                if (closed || stopped || socket !== active) return;

                socket = null;
                ready = false;

                clearTimers();

                if (event.code === 1008) {
                    expireSession();
                    return;
                }

                handlers.onState?.("Disconnected. Reconnecting…");
                handlers.onDisconnect?.();

                const delay = Math.min(
                    30000,
                    1000 * 2 ** attempts++
                );

                retryTimer = setTimeout(connect, delay);
            };
        }

        onCleanup(close);
        connect();

        return Object.freeze({
            send,
            close,
            get ready() {
                return ready && !closed && !stopped;
            }
        });
    }

    function setupAuth() {
        const registering = page === "register";
        const prefix = registering ? "register" : "login";
        const form = element(prefix + "-form");
        const button = element(prefix + "-button");

        let busy = false;

        form.addEventListener("submit", async event => {
            event.preventDefault();

            if (busy || !form.reportValidity()) return;

            const username = element(prefix + "-username").value
                .trim().toLowerCase();

            const password = element(prefix + "-password").value;

            if (registering
                    && password !== element("register-confirm").value) {
                notify("The passwords do not match.", true);
                element("register-confirm").focus();
                return;
            }

            busy = true;
            button.disabled = true;
            notify();

            try {
                await request(
                    "/api/auth/" + (registering ? "register" : "login"),
                    post({ username, password }),
                    false
                );

                element(prefix + "-password").value = "";

                if (registering) {
                    element("register-confirm").value = "";
                }

                cleanup();

                location.assign(
                    registering ? "/login?registered=1" : "/home"
                );

            } catch (error) {
                notify(error.message, true);

            } finally {
                busy = false;
                button.disabled = false;
            }
        });
    }

    async function start() {
        const pages = [
            "login", "register", "home",
            "conversations", "camera", "room"
        ];

        if (!pages.includes(page)) {
            throw new Error("Unknown page.");
        }

        document.querySelectorAll("[data-page]").forEach(section => {
            section.hidden = section.dataset.page !== page;
        });

        if (page === "login" || page === "register") {
            setupAuth();

            const query = new URLSearchParams(location.search);

            if (query.get("registered") === "1") {
                notify("Registration successful. Please log in.");

            } else if (query.get("expired") === "1") {
                notify("Please log in again.", true);

            } else {
                notify();
            }

            return null;
        }

        account = await request("/api/auth/me");

        if (stopped) return null;

        if (!validAccount(account)) {
            throw new Error("The server returned an invalid account.");
        }

        element("current-user").textContent = account.username;
        element("welcome-message").textContent =
            "Welcome, " + account.username + ".";

        element("main-nav").hidden = false;

        const logout = element("logout-button");

        logout.addEventListener("click", async () => {
            logout.disabled = true;

            try {
                await request("/api/auth/logout", post({}), false);

                cleanup();
                location.replace("/login");

            } catch (error) {
                notify(error.message, true);
                logout.disabled = false;
            }
        });

        notify();
        return account;
    }

    window.addEventListener("pagehide", cleanup);

    window.addEventListener("pageshow", event => {
        if (event.persisted) {
            location.reload();
        }
    });

    const ready = start().catch(error => {
        notify(error.message, true);
        return null;
    });

    window.ChatApp = Object.freeze({
        page,
        ready,
        element,
        notify,
        request,
        post,
        loadUsers,
        messageNode,
        openSocket,
        onCleanup,
        validName: value =>
            typeof value === "string" && names.test(value),
        isStopped: () => stopped
    });
})();