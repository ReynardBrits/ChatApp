package com.example.chatapp.server;

import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

public final class PushInstallationsServlet extends JsonServlet {

    private static final long serialVersionUID = 1L;

    private PushInstallationRepository installations;

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

        installations = new PushInstallationRepository(database);
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        UserRepository.Account account =
                AuthServlet.currentAccount(request);

        if (account == null) {
            writeError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Please log in."
            );
            return;
        }

        String path = request.getPathInfo();

        if (!"/register".equals(path) && !"/unregister".equals(path)) {
            writeError(
                    response,
                    HttpServletResponse.SC_NOT_FOUND,
                    "Unknown notification registration route."
            );
            return;
        }

        try {
            JsonObject body = readObject(request);

            String installationId = requiredString(body, "installationId");
            String bindingId = requiredString(body, "bindingId");

            if ("/register".equals(path)) {
                installations.bind(
                        account.username(),
                        installationId,
                        bindingId
                );
            } else {
                installations.unbind(
                        account.username(),
                        installationId,
                        bindingId
                );
            }

            writeJson(
                    response,
                    HttpServletResponse.SC_OK,
                    Map.of("status", "ok", "user", account)
            );

        } catch (IllegalArgumentException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    exception.getMessage()
            );

        } catch (SQLException exception) {
            getServletContext().log(
                    "Unable to update a notification registration.",
                    exception
            );

            writeError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to update notifications. Please try again."
            );
        }
    }
}