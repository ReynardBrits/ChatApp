package com.example.chatapp.server;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

public final class UsersServlet extends JsonServlet {

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
    protected void doGet(
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

        try {
            writeJson(
                    response,
                    HttpServletResponse.SC_OK,
                    users.getOtherUsers(account.id())
            );

        } catch (SQLException exception) {
            getServletContext().log(
                    "Unable to load the user list.",
                    exception
            );

            writeError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load users. Please try again."
            );
        }
    }
}