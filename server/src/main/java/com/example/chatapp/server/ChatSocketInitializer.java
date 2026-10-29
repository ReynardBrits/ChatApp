package com.example.chatapp.server;

import org.apache.tomcat.websocket.server.WsSci;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.websocket.DeploymentException;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;

import java.util.Set;

public final class ChatSocketInitializer
        implements ServletContainerInitializer {

    public static final String SOCKET_PATH = "/ws/chat";
    public static final String ROOM_SOCKET_PATH = "/ws/room";

    @Override
    public void onStartup(
            Set<Class<?>> classes,
            ServletContext context
    ) throws ServletException {

        Object databaseValue = context.getAttribute(
                ServerDatabase.class.getName()
        );

        Object hubValue = context.getAttribute(
                ChatHub.class.getName()
        );

        if (!(databaseValue instanceof ServerDatabase database)
                || !(hubValue instanceof ChatHub hub)) {

            throw new ServletException(
                    "The chat database and connection manager must be initialized."
            );
        }

        Object containerValue = context.getAttribute(
                ServerContainer.class.getName()
        );

        if (containerValue == null) {
            new WsSci().onStartup(Set.of(), context);

            containerValue = context.getAttribute(
                    ServerContainer.class.getName()
            );
        }

        if (!(containerValue instanceof ServerContainer container)) {
            throw new ServletException(
                    "The WebSocket container could not be initialized."
            );
        }

        ServerEndpointConfig endpoint = ServerEndpointConfig.Builder
                .create(ChatSocketEndpoint.class, SOCKET_PATH)
                .configurator(new ChatSocketConfigurator())
                .build();

        endpoint.getUserProperties().put(
                ServerDatabase.class.getName(),
                database
        );

        endpoint.getUserProperties().put(
                ChatHub.class.getName(),
                hub
        );

        ServerEndpointConfig roomEndpoint = ServerEndpointConfig.Builder
                .create(RoomSocketEndpoint.class, ROOM_SOCKET_PATH)
                .configurator(new ChatSocketConfigurator())
                .build();

        roomEndpoint.getUserProperties().put(
                RoomSocketEndpoint.HUB_PROPERTY,
                new ChatHub()
        );

        try {
            container.addEndpoint(endpoint);
            container.addEndpoint(roomEndpoint);

        } catch (DeploymentException exception) {
            throw new ServletException(
                    "Unable to register the chat WebSocket endpoints.",
                    exception
            );
        }

        context.log(
                "Chat WebSocket endpoint registered at " + SOCKET_PATH
        );

        context.log(
                "Live room WebSocket endpoint registered at " + ROOM_SOCKET_PATH
        );
    }
}