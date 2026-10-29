(() => {
    "use strict";

    const app = window.ChatApp;
    if (app.page !== "camera") return;

    app.ready.then(async account => {
        if (!account || app.isStopped()) return;

        const userSelect = app.element("camera-user");
        const startButton = app.element("camera-start");
        const captureButton = app.element("camera-capture");
        const stopButton = app.element("camera-stop");
        const video = app.element("camera-video");
        const canvas = app.element("camera-canvas");
        const fileInput = app.element("photo-file");
        const preview = app.element("photo-preview");
        const status = app.element("camera-status");
        const sendButton = app.element("photo-send");

        const MAX_BYTES = 5 * 1024 * 1024;
        const MAX_SIDE = 1600;
        const imageNamePattern =
            /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png)$/;

        let users = new Set();
        let loadingUsers = true;
        let starting = false;
        let preparing = false;
        let uploading = false;
        let stream = null;
        let cameraVersion = 0;
        let photoVersion = 0;
        let photo = null;
        let previewUrl = "";
        let pendingFileUrl = "";

        function setStatus(message, error = false) {
            status.textContent = message;
            status.classList.toggle("error", error);
        }

        function updateControls() {
            const busy = loadingUsers || preparing || uploading;

            userSelect.disabled =
                loadingUsers || uploading || users.size === 0;

            startButton.disabled =
                busy || starting || stream !== null;

            captureButton.disabled =
                busy || starting || stream === null
                || video.videoWidth === 0
                || video.videoHeight === 0;

            stopButton.disabled = stream === null && !starting;
            fileInput.disabled = busy;

            sendButton.disabled =
                busy || photo === null || !users.has(userSelect.value);

            startButton.textContent =
                starting ? "Starting…" : "Start camera";

            sendButton.textContent =
                uploading ? "Sending…" : "Send photo";
        }

        function stopCamera() {
            ++cameraVersion;
            starting = false;

            if (stream) {
                stream.getTracks().forEach(track => track.stop());
                stream = null;
            }

            video.pause();
            video.srcObject = null;
            video.hidden = true;
            updateControls();
        }

        function clearPhoto() {
            photo = null;
            preview.hidden = true;
            preview.removeAttribute("src");

            if (previewUrl) {
                URL.revokeObjectURL(previewUrl);
            }

            previewUrl = "";
        }

        function currentPhoto(version) {
            return !app.isStopped() && version === photoVersion;
        }

        async function startCamera() {
            if (loadingUsers || starting || preparing || uploading || stream) {
                return;
            }

            const version = ++cameraVersion;
            starting = true;
            setStatus("Allow camera access when your browser asks.");
            updateControls();

            try {
                if (!navigator.mediaDevices?.getUserMedia) {
                    throw new Error("Camera access is unavailable.");
                }

                const opened = await navigator.mediaDevices.getUserMedia({
                    video: { facingMode: "environment" },
                    audio: false
                });

                if (app.isStopped() || version !== cameraVersion) {
                    opened.getTracks().forEach(track => track.stop());
                    return;
                }

                stream = opened;
                video.srcObject = opened;
                video.hidden = false;

                opened.getVideoTracks().forEach(track => {
                    track.addEventListener("ended", () => {
                        if (stream !== opened || app.isStopped()) return;

                        stopCamera();
                        setStatus(
                            "Camera stopped. You can start it again or choose a photo."
                        );
                    });
                });

                await video.play();

                if (app.isStopped() || version !== cameraVersion) return;

                setStatus("Camera ready. Press Capture to take a photo.");
            } catch {
                if (app.isStopped() || version !== cameraVersion) return;

                stopCamera();
                setStatus(
                    "Could not open the camera. Allow camera access, or use Choose file.",
                    true
                );
            } finally {
                if (!app.isStopped() && version === cameraVersion) {
                    starting = false;
                    updateControls();
                }
            }
        }

        async function preparePhoto(file = null) {
            if (loadingUsers || preparing || uploading || app.isStopped()) {
                return;
            }

            const version = ++photoVersion;
            preparing = true;
            clearPhoto();
            setStatus("Preparing photo…");
            updateControls();

            let sourceUrl = "";

            try {
                let source = video;
                let width = video.videoWidth;
                let height = video.videoHeight;

                if (file) {
                    if (!["image/jpeg", "image/png"].includes(file.type)) {
                        throw new Error("Choose a JPEG or PNG image.");
                    }

                    if (file.size === 0 || file.size > MAX_BYTES) {
                        throw new Error("Choose an image smaller than 5 MB.");
                    }

                    sourceUrl = URL.createObjectURL(file);
                    pendingFileUrl = sourceUrl;

                    const image = new Image();

                    await new Promise((resolve, reject) => {
                        image.onload = resolve;
                        image.onerror = () => reject(
                            new Error("The selected image could not be opened.")
                        );
                        image.src = sourceUrl;
                    });

                    source = image;
                    width = image.naturalWidth;
                    height = image.naturalHeight;
                } else if (!stream) {
                    throw new Error(
                        "Start the camera before capturing a photo."
                    );
                }

                if (!currentPhoto(version)) return;

                if (!Number.isFinite(width)
                        || !Number.isFinite(height)
                        || width <= 0
                        || height <= 0) {
                    throw new Error("The image is not ready. Try again.");
                }

                const scale = Math.min(
                    1,
                    MAX_SIDE / Math.max(width, height)
                );

                canvas.width = Math.max(1, Math.round(width * scale));
                canvas.height = Math.max(1, Math.round(height * scale));

                const context = canvas.getContext("2d", { alpha: false });

                if (!context) {
                    throw new Error(
                        "Your browser could not prepare the photo."
                    );
                }

                context.fillStyle = "#ffffff";
                context.fillRect(0, 0, canvas.width, canvas.height);
                context.drawImage(
                    source,
                    0,
                    0,
                    canvas.width,
                    canvas.height
                );

                const prepared = await new Promise(resolve =>
                    canvas.toBlob(resolve, "image/jpeg", 0.85)
                );

                if (!currentPhoto(version)) return;

                if (!prepared
                        || prepared.size === 0
                        || prepared.size > MAX_BYTES
                        || !["image/jpeg", "image/png"].includes(prepared.type)) {
                    throw new Error(
                        "The photo could not be prepared within the 5 MB limit."
                    );
                }

                previewUrl = URL.createObjectURL(prepared);
                photo = prepared;
                preview.src = previewUrl;
                preview.hidden = false;

                setStatus(
                    "Photo ready. Choose the recipient, then press Send photo."
                );
            } catch (error) {
                if (currentPhoto(version)) {
                    setStatus(error.message, true);
                }
            } finally {
                if (sourceUrl) {
                    URL.revokeObjectURL(sourceUrl);
                }

                if (pendingFileUrl === sourceUrl) {
                    pendingFileUrl = "";
                }

                if (currentPhoto(version)) {
                    preparing = false;
                    updateControls();
                }
            }
        }

        async function sendPhoto() {
            const receiver = userSelect.value;

            if (loadingUsers
                    || preparing
                    || uploading
                    || !photo
                    || !users.has(receiver)) {
                return;
            }

            const selectedPhoto = photo;
            uploading = true;
            setStatus("Sending photo…");
            updateControls();

            try {
                const message = await app.request(
                    "/api/photos?receiver=" + encodeURIComponent(receiver),
                    {
                        method: "POST",
                        headers: {
                            "Content-Type": selectedPhoto.type
                        },
                        body: selectedPhoto
                    }
                );

                if (app.isStopped()) return;

                if (!message
                        || !Number.isSafeInteger(message.id)
                        || message.id <= 0
                        || message.sender !== account.username
                        || message.receiver !== receiver
                        || !Number.isSafeInteger(message.timestamp)
                        || message.timestamp <= 0
                        || typeof message.imageName !== "string"
                        || !imageNamePattern.test(message.imageName)) {
                    throw new Error(
                        "The server returned an invalid photo confirmation."
                    );
                }

                stopCamera();
                clearPhoto();
                setStatus("Photo sent. Opening the conversation…");

                window.location.assign(
                    "/conversations?with=" + encodeURIComponent(receiver)
                );
            } catch (error) {
                if (!app.isStopped()) {
                    setStatus(
                        "Could not confirm sending: " + error.message
                        + " Check the conversation before sending again.",
                        true
                    );
                }
            } finally {
                if (!app.isStopped()) {
                    uploading = false;
                    updateControls();
                }
            }
        }

        startButton.addEventListener("click", startCamera);

        captureButton.addEventListener("click", () => preparePhoto());

        stopButton.addEventListener("click", () => {
            stopCamera();

            setStatus(
                photo
                    ? "Camera stopped. Your photo is ready to send."
                    : "Camera stopped. Start it again or choose a photo."
            );
        });

        video.addEventListener("loadedmetadata", updateControls);
        video.addEventListener("resize", updateControls);

        fileInput.addEventListener("change", () => {
            const file = fileInput.files?.[0];
            fileInput.value = "";

            if (file) preparePhoto(file);
        });

        sendButton.addEventListener("click", sendPhoto);

        userSelect.addEventListener("change", () => {
            updateControls();

            const receiver = users.has(userSelect.value)
                ? userSelect.value
                : "";

            window.history.replaceState(
                null,
                "",
                "/camera"
                + (receiver ? "?with=" + encodeURIComponent(receiver) : "")
            );
        });

        app.onCleanup(() => {
            ++photoVersion;
            stopCamera();
            clearPhoto();

            if (pendingFileUrl) {
                URL.revokeObjectURL(pendingFileUrl);
            }

            pendingFileUrl = "";
            canvas.width = 0;
            canvas.height = 0;
        });

        setStatus("Loading recipients…");
        updateControls();

        try {
            const accounts = await app.loadUsers(userSelect);

            if (app.isStopped()) return;

            users = new Set(accounts.map(user => user.username));

            setStatus(
                users.size
                    ? "Choose a recipient, then capture or select a photo."
                    : "Register another account before sending a photo."
            );
        } catch (error) {
            if (!app.isStopped()) {
                setStatus(
                    "Could not load recipients: " + error.message
                    + " Reload this page to try again.",
                    true
                );
            }
        } finally {
            if (!app.isStopped()) {
                loadingUsers = false;
                updateControls();
            }
        }
    }).catch(error => app.notify(error.message, true));
})();