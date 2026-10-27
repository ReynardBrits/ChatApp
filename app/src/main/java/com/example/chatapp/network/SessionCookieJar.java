package com.example.chatapp.network;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public final class SessionCookieJar implements CookieJar {

    private static final String SESSION_COOKIE_NAME = "CHATAPPSESSION";

    private final URI server = URI.create(ServerConfig.BASE_URL);

    private Cookie sessionCookie;

    @Override
    public synchronized void saveFromResponse(
            HttpUrl url,
            List<Cookie> cookies
    ) {
        if (!isOurServer(url)) {
            return;
        }

        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE_NAME.equals(cookie.name())
                    && cookie.matches(url)) {

                sessionCookie = cookie.expiresAt()
                        > System.currentTimeMillis()
                        ? cookie
                        : null;
            }
        }
    }

    @Override
    public synchronized List<Cookie> loadForRequest(HttpUrl url) {
        if (!isOurServer(url)) {
            return Collections.emptyList();
        }

        if (sessionCookie == null) {
            return Collections.emptyList();
        }

        if (sessionCookie.expiresAt() <= System.currentTimeMillis()) {
            sessionCookie = null;
            return Collections.emptyList();
        }

        if (!sessionCookie.matches(url)) {
            return Collections.emptyList();
        }

        return Collections.singletonList(sessionCookie);
    }

    public synchronized void clear() {
        sessionCookie = null;
    }

    private boolean isOurServer(HttpUrl url) {
        return server.getScheme().equalsIgnoreCase(url.scheme())
                && server.getHost().equalsIgnoreCase(url.host())
                && server.getPort() == url.port();
    }
}