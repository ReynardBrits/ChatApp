package com.example.chatapp.server;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.http.Rfc6265CookieProcessor;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ChatServer {

    private static final int PORT = readPort();

    private ChatServer() {}

    private static int readPort() {
        String value = System.getenv("PORT");

        if (value == null || value.isBlank()) {
            return 8081;
        }

        try {
            int port = Integer.parseInt(value.trim());

            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException ignored) {
            // Invalid settings are reported below.
        }

        throw new IllegalArgumentException(
                "PORT must be a whole number from 1 to 65535."
        );
    }

    private static Path readDataDirectory() {
        String value = System.getenv("CHATAPP_DATA_DIR");

        String directory = value == null || value.isBlank()
                ? "data"
                : value.trim();

        return Path.of(directory).toAbsolutePath().normalize();
    }

    public static void main(String[] args) throws Exception {
        Path dataDirectory = readDataDirectory();

        ServerDatabase database = new ServerDatabase(dataDirectory);
        PhotoStorage photos = new PhotoStorage(
                dataDirectory.resolve("photos")
        );
        PushNotificationService pushNotifications =
                new PushNotificationService(database);

        ChatHub hub = new ChatHub();
        hub.setPushNotifications(pushNotifications);

        Path runtimeDirectory = Path.of("target", "tomcat")
                .toAbsolutePath();

        Path webRoot = runtimeDirectory.resolve("webroot");
        Files.createDirectories(webRoot);

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(runtimeDirectory.toString());
        tomcat.setPort(PORT);
        tomcat.getConnector();

        if ("true".equalsIgnoreCase(
                System.getenv("CHATAPP_HTTPS_PROXY")
        )) {
            tomcat.getConnector().setScheme("https");
            tomcat.getConnector().setSecure(true);
            tomcat.getConnector().setProxyPort(443);
        }

        Context context = tomcat.addContext("", webRoot.toString());

        context.setParentClassLoader(
                ChatServer.class.getClassLoader()
        );

        context.getServletContext().setAttribute(
                ServerDatabase.class.getName(),
                database
        );

        context.getServletContext().setAttribute(
                PhotoStorage.class.getName(),
                photos
        );

        context.getServletContext().setAttribute(
                ChatHub.class.getName(),
                hub
        );

        context.setSessionCookieName("CHATAPPSESSION");
        context.setUseHttpOnly(true);

        Rfc6265CookieProcessor cookies =
                new Rfc6265CookieProcessor();

        cookies.setSameSiteCookies("lax");
        context.setCookieProcessor(cookies);

        StandardManager sessions = new StandardManager();
        sessions.setPathname(null);
        context.setManager(sessions);

        Wrapper defaultServlet = Tomcat.addServlet(
                context,
                "default",
                new DefaultServlet()
        );

        defaultServlet.setLoadOnStartup(1);
        defaultServlet.setAsyncSupported(true);
        defaultServlet.addInitParameter("listings", "false");
        defaultServlet.addInitParameter("readonly", "true");

        context.addServletMappingDecoded("/", "default");
        context.addWelcomeFile("index.html");

        context.addServletContainerInitializer(
                new ChatSocketInitializer(),
                null
        );

        HttpServlet healthServlet = new HttpServlet() {
            @Override
            protected void doGet(
                    HttpServletRequest request,
                    HttpServletResponse response
            ) throws IOException {

                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Cache-Control", "no-store");

                response.getWriter().write(
                        "{\"status\":\"ok\"}"
                );
            }
        };

        Tomcat.addServlet(context, "health", healthServlet);
        context.addServletMappingDecoded("/api/health", "health");

        Tomcat.addServlet(context, "auth", new AuthServlet())
                .setLoadOnStartup(1);

        context.addServletMappingDecoded("/api/auth/*", "auth");

        Tomcat.addServlet(context, "users", new UsersServlet())
                .setLoadOnStartup(1);

        context.addServletMappingDecoded("/api/users", "users");

        Tomcat.addServlet(context, "messages", new MessagesServlet())
                .setLoadOnStartup(1);

        context.addServletMappingDecoded("/api/messages", "messages");

        Tomcat.addServlet(context, "photos", new PhotosServlet())
                .setLoadOnStartup(1);

        context.addServletMappingDecoded("/api/photos/*", "photos");

        Tomcat.addServlet(context, "conversations", new ConversationsServlet())
                .setLoadOnStartup(1);

        context.addServletMappingDecoded(
                "/api/conversations/*",
                "conversations"
        );

        Tomcat.addServlet(context, "push", new PushInstallationsServlet())
                .setLoadOnStartup(1);

        context.addServletMappingDecoded("/api/push/*", "push");

        Wrapper pagesServlet = Tomcat.addServlet(
                context,
                "pages",
                new PageServlet()
        );

        pagesServlet.setLoadOnStartup(1);
        pagesServlet.setAsyncSupported(true);

        String[] browserRoutes = {
                "",
                "/login",
                "/register",
                "/home",
                "/conversations",
                "/camera",
                "/room",
                "/assets/app.css",
                "/assets/app.js",
                "/assets/conversations.js",
                "/assets/camera.js",
                "/assets/room.js"
        };

        for (String route : browserRoutes) {
            context.addServletMappingDecoded(route, "pages");
        }

        try {
            FirebaseSetup.initialize();
            tomcat.start();

            if (!context.getState().isAvailable()
                    || !tomcat.getConnector().getState().isAvailable()) {

                throw new IllegalStateException(
                        "ChatApp server failed to start. Check the errors above."
                );
            }

        } catch (Exception startupFailure) {
            try {
                tomcat.stop();
            } catch (Exception cleanupFailure) {
                startupFailure.addSuppressed(cleanupFailure);
            }

            try {
                tomcat.destroy();
            } catch (Exception cleanupFailure) {
                startupFailure.addSuppressed(cleanupFailure);
            }

            pushNotifications.close();
            FirebaseSetup.close();
            throw startupFailure;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (Exception exception) {
                exception.printStackTrace();
            } finally {
                pushNotifications.close();
                FirebaseSetup.close();
            }
        }, "chatapp-shutdown"));

        System.out.println(
                "Database ready: " + database.getDatabasePath()
        );

        System.out.println(
                "ChatApp server running: http://localhost:"
                        + PORT + "/api/health"
        );

        System.out.println(
                "WebSocket endpoint: ws://localhost:"
                        + PORT + ChatSocketInitializer.SOCKET_PATH
        );

        System.out.println(
                "Photo endpoint: http://localhost:"
                        + PORT + "/api/photos"
        );

        tomcat.getServer().await();
    }
}