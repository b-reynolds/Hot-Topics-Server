package io.benreynolds.hottopics;

import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;

@ServerEndpoint("/echo")
public class EchoEndpoint {

    private Session mSession;

    @OnOpen
    public void onOpen(Session session) {
        mSession = session;
    }

    @OnMessage
    public void onMessage(String message) {
        System.out.println("[" + mSession.getId() + "]: " + message);
        if(mSession != null && mSession.isOpen()) {
            try {
                mSession.getBasicRemote().sendText(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}