package com.example.chatapp.server;

import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Map;

public final class AuthServlet extends JsonServlet {

    public static final String SESSION_ACCOUNT = "chatapp.account";

    private static final int SESSION_TIMEOUT_SECONDS = 60 * 60;

    private UserRepository users;

    @Override
    public void init() throws ServletException {
        super.init();

        Object value = getServletContext().getAttribute(
                ServerDatabase.class.getName()
        );

        if (!(value instanceof ServerDatabase database)) {
            throw new ServletException(
                    "The server database has not been initialized."
            );
        }

        users = new UserRepository(database);
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        String path = request.getPathInfo();

        if (!"/register".equals(path)
                && !"/login".equals(path)
                && !"/logout".equals(path)) {
            writeError(
                    response,
                    HttpServletResponse.SC_NOT_FOUND,
                    "Unknown authentication route."
            );
            return;
        }

        try {
            // Logout also accepts a JSON object: {}
            JsonObject body = readObject(request);

            switch (path) {
                case "/register" -> {
                    UserRepository.Account account = users.register(
                            requiredString(body, "username"),
                            requiredString(body, "password")
                    );

                    writeJson(
                            response,
                            HttpServletResponse.SC_CREATED,
                            account
                    );
                }

                case "/login" -> {
                    UserRepository.Account account = users.authenticate(
                            requiredString(body, "username"),
                            requiredString(body, "password")
                    );

                    if (account == null) {
                        writeError(
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "Invalid username or password."
                        );
                        return;
                    }

                    // Replace any previous session after a successful login.
                    endSession(request);

                    HttpSession session = request.getSession(true);
                    session.setMaxInactiveInterval(SESSION_TIMEOUT_SECONDS);
                    session.setAttribute(SESSION_ACCOUNT, account);

                    writeJson(
                            response,
                            HttpServletResponse.SC_OK,
                            account
                    );
                }

                case "/logout" -> {
                    endSession(request);

                    writeJson(
                            response,
                            HttpServletResponse.SC_OK,
                            Map.of("status", "ok")
                    );
                }
            }

        } catch (IllegalArgumentException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    exception.getMessage()
            );

        } catch (SQLException | GeneralSecurityException exception) {
            getServletContext().log(
                    "Authentication request failed.",
                    exception
            );

            writeError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to process the request. Please try again."
            );
        }
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        if (!"/me".equals(request.getPathInfo())) {
            writeError(
                    response,
                    HttpServletResponse.SC_NOT_FOUND,
                    "Unknown authentication route."
            );
            return;
        }

        UserRepository.Account account = currentAccount(request);

        if (account == null) {
            writeError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Please log in."
            );
            return;
        }

        writeJson(response, HttpServletResponse.SC_OK, account);
    }

    public static UserRepository.Account currentAccount(
            HttpServletRequest request
    ) {
        try {
            HttpSession session = request.getSession(false);

            if (session == null) {
                return null;
            }

            Object value = session.getAttribute(SESSION_ACCOUNT);

            return value instanceof UserRepository.Account account
                    ? account
                    : null;

        } catch (IllegalStateException exception) {
            // Another request may have already ended this session.
            return null;
        }
    }

    private static void endSession(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);

            if (session != null) {
                session.invalidate();
            }

        } catch (IllegalStateException exception) {
            // The session has already ended.
        }
    }
}