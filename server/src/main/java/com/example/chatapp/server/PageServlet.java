package com.example.chatapp.server;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class PageServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; "
                    + "script-src 'self'; "
                    + "style-src 'self'; "
                    + "img-src 'self' blob:; "
                    + "media-src 'self' blob:; "
                    + "connect-src 'self' "
                    + "ws://localhost:8081 "
                    + "ws://127.0.0.1:8081 "
                    + "ws://10.0.2.2:8081; "
                    + "object-src 'none'; "
                    + "base-uri 'none'; "
                    + "frame-ancestors 'none'; "
                    + "form-action 'self'";

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");

        response.setHeader(
                "Content-Security-Policy",
                CONTENT_SECURITY_POLICY
        );

        String path = request.getServletPath();

        UserRepository.Account account =
                AuthServlet.currentAccount(request);

        if (path == null || path.isEmpty() || "/".equals(path)) {
            redirect(
                    response,
                    account == null ? "/login" : "/home"
            );
            return;
        }

        PageResource resource = resolveResource(path);

        if (resource == null) {
            response.sendError(
                    HttpServletResponse.SC_NOT_FOUND,
                    "Page not found."
            );
            return;
        }

        if (resource.requiresLogin() && account == null) {
            redirect(response, "/login");
            return;
        }

        if (account != null
                && ("/login".equals(path)
                || "/register".equals(path))) {
            redirect(response, "/home");
            return;
        }

        try (InputStream input = PageServlet.class.getResourceAsStream(
                resource.classpath()
        )) {
            if (input == null) {
                response.sendError(
                        HttpServletResponse.SC_NOT_FOUND,
                        "Page resource not found."
                );
                return;
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(resource.contentType());

            input.transferTo(response.getOutputStream());
        }
    }

    private static PageResource resolveResource(String path) {
        // Only these fixed resources can be served.
        return switch (path) {
            case "/login", "/register" -> new PageResource(
                    "/web/index.html",
                    "text/html",
                    false
            );

            case "/home", "/conversations", "/camera", "/room" ->
                    new PageResource(
                            "/web/index.html",
                            "text/html",
                            true
                    );

            case "/assets/app.js" -> new PageResource(
                    "/web/app.js",
                    "text/javascript",
                    false
            );

            case "/assets/app.css" -> new PageResource(
                    "/web/app.css",
                    "text/css",
                    false
            );

            case "/assets/conversations.js" -> new PageResource(
                    "/web/conversations.js",
                    "text/javascript",
                    false
            );

            case "/assets/camera.js" -> new PageResource(
                    "/web/camera.js",
                    "text/javascript",
                    false
            );

            case "/assets/room.js" -> new PageResource(
                    "/web/room.js",
                    "text/javascript",
                    false
            );

            default -> null;
        };
    }

    private static void redirect(
            HttpServletResponse response,
            String destination
    ) {
        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
        response.setHeader("Location", destination);
    }

    private record PageResource(
            String classpath,
            String contentType,
            boolean requiresLogin
    ) {}
}