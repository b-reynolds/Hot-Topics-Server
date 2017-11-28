package io.benreynolds.hottopics.server;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import java.io.IOException;

import java.util.HashMap;
import java.util.Map;

@ServerEndpoint("/chat")
public class ChatEndpoint {

    private static Map<Session, Object> mUsers = new HashMap<>();

    @OnOpen
    public void onOpen(Session session) throws IOException {
        String message = String.format("[%s] joined the chat room.", session.getId());
        mUsers.put(session, null);
        for (Map.Entry<Session, Object> entry : mUsers.entrySet()) {
            entry.getKey().getBasicRemote().sendText(message);
        }
        System.out.println(message);
    }

    @OnMessage
    public void onMessage(String message, Session session) throws IOException {
        System.out.println(String.format("[%s]: %s", session.getId(), message));
        for (Map.Entry<Session, Object> entry : mUsers.entrySet()) {
            entry.getKey().getBasicRemote().sendText(String.format("[%s]: %s", session.getId(), message));
            System.out.println(String.format("Sent '%s' to '%s'.", message, entry.getKey().getId()));
        }
    }

    @OnClose
    public void onClose(Session session) throws IOException {
        String message = String.format("[%s] left the chat room.", session.getId());
        mUsers.remove(session);
        for (Map.Entry<Session, Object> entry : mUsers.entrySet()) {
            entry.getKey().getBasicRemote().sendText(message);
        }
        System.out.println(message);
    }

}
