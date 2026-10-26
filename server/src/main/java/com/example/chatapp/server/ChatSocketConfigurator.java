package com.example.chatapp.server;

import javax.servlet.http.HttpSession;
import javax.websocket.EndpointConfig;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

import java.net.URI;
import java.util.List;
import java.util.Map;

public final class ChatSocketConfigurator
        extends ServerEndpointConfig.Configurator {

    public static final String HTTP_SESSION =
            "chatapp.websocket.httpSession";

    @Override
    public void modifyHandshake(
            ServerEndpointConfig config,
            HandshakeRequest request,
            HandshakeResponse response
    ) {
        config.getUserProperties().remove(HTTP_SESSION);

        if (!hasAllowedOrigin(request)) {
            return;
        }

        Object value = request.getHttpSession();

        if (value instanceof HttpSession session) {
            config.getUserProperties().put(HTTP_SESSION, session);
        }
    }

    public static HttpSession getHttpSession(EndpointConfig config) {
        Object value = config.getUserProperties().get(HTTP_SESSION);

        return value instanceof HttpSession session ? session : null;
    }

    public static UserRepository.Account currentAccount(
            HttpSession session
    ) {
        if (session == null) {
            return null;
        }

        try {
            Object value = session.getAttribute(
                    AuthServlet.SESSION_ACCOUNT
            );

            return value instanceof UserRepository.Account account
                    ? account
                    : null;

        } catch (IllegalStateException exception) {
            return null;
        }
    }

    private boolean hasAllowedOrigin(HandshakeRequest request) {
        List<String> origins = null;

        for (Map.Entry<String, List<String>> header
                : request.getHeaders().entrySet()) {

            if ("Origin".equalsIgnoreCase(header.getKey())) {
                origins = header.getValue();
                break;
            }
        }

        // Native clients may connect without an Origin header.
        if (origins == null) {
            return true;
        }

        if (origins.size() != 1) {
            return false;
        }

        try {
            URI origin = URI.create(origins.get(0));
            URI target = request.getRequestURI();

            if (origin.getHost() == null || target.getHost() == null) {
                return false;
            }

            if (origin.getUserInfo() != null
                    || origin.getQuery() != null
                    || origin.getFragment() != null) {
                return false;
            }

            String path = origin.getPath();

            if (path != null && !path.isEmpty()) {
                return false;
            }

            String expectedScheme =
                    "wss".equalsIgnoreCase(target.getScheme())
                            ? "https"
                            : "http";

            return expectedScheme.equalsIgnoreCase(origin.getScheme())
                    && target.getHost().equalsIgnoreCase(origin.getHost())
                    && effectivePort(origin) == effectivePort(target);

        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }

        boolean secure = "https".equalsIgnoreCase(uri.getScheme())
                || "wss".equalsIgnoreCase(uri.getScheme());

        return secure ? 443 : 80;
    }
}