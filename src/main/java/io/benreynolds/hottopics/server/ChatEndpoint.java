package io.benreynolds.hottopics.server;

import javax.websocket.EncodeException;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static java.lang.String.format;

@ServerEndpoint(value = "/chat", encoders = MessageEncoder.class, decoders = MessageDecoder.class)
public class ChatEndpoint {

    private static Set<Session> mPeers = Collections.synchronizedSet(new HashSet<Session>());

    @OnOpen
    public void onOpen(Session session) {
        System.out.println(String.format("%s joined the chat room.", session.getId()));
        mPeers.add(session);
    }

    @OnMessage
    public void onMessage(Message message, Session session) throws IOException, EncodeException {
        for (Session peer : mPeers) {
            if (!session.getId().equals(peer.getId())) {
                peer.getBasicRemote().sendObject(message);
            }
        }
    }

    @OnClose
    public void onClose(Session session) throws IOException, EncodeException {
        System.out.println(format("%s left the chat room.", session.getId()));
        mPeers.remove(session);
        for (Session peer : mPeers) {
            Message message = new Message();
            message.setSender("Server");
            message.setContent(format("%s left the chat room", (String) session.getUserProperties().get("user")));
            message.setReceived(new Date());
            peer.getBasicRemote().sendObject(message);
        }
    }

}
