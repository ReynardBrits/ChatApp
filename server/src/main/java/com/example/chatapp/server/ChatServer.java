package com.example.chatapp.server;

import org.apache.catalina.Context;
import org.apache.catalina.session.StandardManager;
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

    private ChatServer() {
    }

    public static void main(String[] args) throws Exception {
        ServerDatabase database = new ServerDatabase(Path.of("data"));

        Path runtimeDirectory = Path.of("target", "tomcat").toAbsolutePath();
        Path webRoot = runtimeDirectory.resolve("webroot");
        Files.createDirectories(webRoot);

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(runtimeDirectory.toString());
        tomcat.setPort(PORT);
        tomcat.getConnector();

        Context context = tomcat.addContext("", webRoot.toString());
        context.setParentClassLoader(ChatServer.class.getClassLoader());

        context.getServletContext().setAttribute(
                ServerDatabase.class.getName(),
                database
        );

        // Configure the cookie used to identify logged-in clients.
        context.setSessionCookieName("CHATAPPSESSION");
        context.setUseHttpOnly(true);

        Rfc6265CookieProcessor cookies = new Rfc6265CookieProcessor();
        cookies.setSameSiteCookies("lax");
        context.setCookieProcessor(cookies);

        // Keep login sessions in memory.
        StandardManager sessions = new StandardManager();
        sessions.setPathname(null);
        context.setManager(sessions);

        HttpServlet healthServlet = new HttpServlet() {
            @Override
            protected void doGet(
                    HttpServletRequest request,
                    HttpServletResponse response
            ) throws IOException {
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Cache-Control", "no-store");
                response.getWriter().write("{\"status\":\"ok\"}");
            }
        };

        Tomcat.addServlet(context, "health", healthServlet);
        context.addServletMappingDecoded("/api/health", "health");

        // Register the authentication servlet.
        Tomcat.addServlet(context, "auth", new AuthServlet())
                .setLoadOnStartup(1);

        context.addServletMappingDecoded("/api/auth/*", "auth");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }, "chatapp-shutdown"));

        tomcat.start();

        System.out.println("Database ready: " + database.getDatabasePath());
        System.out.println(
                "ChatApp server running: http://localhost:" + PORT + "/api/health"
        );

        tomcat.getServer().await();
    }
}