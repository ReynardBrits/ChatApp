package com.example.chatapp.server;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Locale;

public final class PhotosServlet extends JsonServlet {

    private PhotoStorage storage;
    private PhotoMessageRepository messages;
    private ChatHub hub;

    @Override
    public void init() throws ServletException {
        super.init();

        Object databaseValue = getServletContext().getAttribute(
                ServerDatabase.class.getName()
        );

        Object storageValue = getServletContext().getAttribute(
                PhotoStorage.class.getName()
        );

        Object hubValue = getServletContext().getAttribute(
                ChatHub.class.getName()
        );

        if (!(databaseValue instanceof ServerDatabase database)
                || !(storageValue instanceof PhotoStorage photoStorage)
                || !(hubValue instanceof ChatHub chatHub)) {

            throw new ServletException(
                    "The photo service has not been initialized."
            );
        }

        storage = photoStorage;
        messages = new PhotoMessageRepository(database);
        hub = chatHub;
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        UserRepository.Account account =
                AuthServlet.currentAccount(request);

        if (account == null) {
            writeError(response, 401, "Please log in.");
            return;
        }

        String path = request.getPathInfo();

        if (path != null && !path.equals("/")) {
            writeError(response, 404, "Unknown photo route.");
            return;
        }

        String receiver = request.getParameter("receiver");

        if (receiver == null || receiver.isBlank()) {
            writeError(response, 400, "Choose a receiver.");
            return;
        }

        String contentType = request.getContentType();

        if (contentType == null) {
            writeError(response, 415, "Upload a JPEG or PNG photo.");
            return;
        }

        contentType = contentType.split(";", 2)[0]
                .trim()
                .toLowerCase(Locale.ROOT);

        if (!contentType.equals("image/jpeg")
                && !contentType.equals("image/png")) {

            writeError(response, 415, "Upload a JPEG or PNG photo.");
            return;
        }

        if (request.getContentLengthLong() > PhotoStorage.MAX_BYTES) {
            writeError(response, 413, "Photos cannot exceed 5 MB.");
            return;
        }

        PhotoStorage.StoredPhoto photo = null;
        boolean persisted = false;

        try {
            photo = storage.store(request.getInputStream());

            MessageRepository.ChatMessage message =
                    messages.sendPhoto(
                            account.username(),
                            receiver,
                            photo.name()
                    );

            persisted = true;

            hub.publish(message);
            writeJson(response, 201, message);

        } catch (IllegalArgumentException exception) {
            if (!persisted) {
                cleanupUnsentPhoto(photo, account.username());
            }

            writeError(response, 400, exception.getMessage());

        } catch (SQLException | IOException exception) {
            if (!persisted) {
                cleanupUnsentPhoto(photo, account.username());
            }

            getServletContext().log(
                    "Unable to process a photo upload.",
                    exception
            );

            if (!response.isCommitted()) {
                writeError(
                        response,
                        500,
                        "Unable to share the photo. Please try again."
                );
            }
        }
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        UserRepository.Account account =
                AuthServlet.currentAccount(request);

        if (account == null) {
            writeError(response, 401, "Please log in.");
            return;
        }

        String path = request.getPathInfo();

        if (path == null || path.length() <= 1) {
            writeError(response, 404, "Photo not found.");
            return;
        }

        String name = path.substring(1);

        if (name.contains("..")
                || name.contains("/")
                || name.contains("\\")) {
            writeError(response, 404, "Photo not found.");
            return;
        }

        try {
            Path file = storage.resolve(name);

            if (!messages.canViewPhoto(account.username(), name)
                    || !Files.isRegularFile(file)) {

                writeError(response, 404, "Photo not found.");
                return;
            }

            response.setStatus(200);
            response.setContentType(
                    name.endsWith(".jpg") ? "image/jpeg" : "image/png"
            );

            response.setContentLengthLong(Files.size(file));
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline");


            Files.copy(file, response.getOutputStream());

        } catch (IllegalArgumentException exception) {
            writeError(response, 404, "Photo not found.");

        } catch (SQLException exception) {
            getServletContext().log(
                    "Unable to check photo access.",
                    exception
            );

            writeError(
                    response,
                    500,
                    "Unable to load the photo. Please try again."
            );
        }
    }

    private void cleanupUnsentPhoto(
            PhotoStorage.StoredPhoto photo,
            String sender
    ) {
        if (photo == null) {
            return;
        }

        try {
            // Keep the file if its message reached the database.
            if (!messages.canViewPhoto(sender, photo.name())) {
                storage.delete(photo.name());
            }

        } catch (SQLException | IOException exception) {
            getServletContext().log(
                    "Unable to clean up an unused photo.",
                    exception
            );
        }
    }
}