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

    private static final int PORT = 8081;

    private ChatServer() {}

    public static void main(String[] args) throws Exception {
        ServerDatabase database = new ServerDatabase(Path.of("data"));
        PhotoStorage photos = new PhotoStorage(Path.of("data", "photos"));
        ChatHub hub = new ChatHub();

        Path runtimeDirectory = Path.of("target", "tomcat")
                .toAbsolutePath();

        Path webRoot = runtimeDirectory.resolve("webroot");
        Files.createDirectories(webRoot);

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(runtimeDirectory.toString());
        tomcat.setPort(PORT);
        tomcat.getConnector();

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

        try {
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

            throw startupFailure;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (Exception exception) {
                exception.printStackTrace();
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