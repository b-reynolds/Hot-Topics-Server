package io.benreynolds.hottopics.server;

import com.google.gson.Gson;

import io.benreynolds.hottopics.packets.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import twitter4j.Trend;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@ServerEndpoint("/chat")
public class ChatEndpoint {

    private final Logger mLogger = LoggerFactory.getLogger(ChatEndpoint.class);

    private static final Set<Session> mUsers = Collections.synchronizedSet(new HashSet<Session>());

    private static TrendManager mTrendManager = new TrendManager();

    private static final String PROPERTY_USERNAME = "Username";
    private static final String PROPERTY_ROOM = "Room";

    private static Set<Chatroom> mChatrooms = Collections.synchronizedSet(new HashSet<Chatroom>());

    private boolean sendMessage(final String message, final Session session) {
        try {
            session.getBasicRemote().sendText(message);
        }
        catch(IOException exception) {
            mLogger.error(String.format("Failed to send message: %s", exception.getMessage()));
            return false;
        }
        return true;
    }

    @OnOpen
    public void onOpen(Session session) {
        mLogger.info(String.format("[%s] opened.", session.getId()));
        mUsers.add(session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        if(mChatrooms.isEmpty()) {
            Trend[] currentTrends = mTrendManager.getTrends().getTrends();
            for(int i = 0; i < (currentTrends.length < 10 ? currentTrends.length : 10); i++) {
                mChatrooms.add(new Chatroom(currentTrends[i].getName()));
            }
        }

        Map<String, Object> userProperties = session.getUserProperties();
        Gson gson = new Gson();
        UnidentifiedPacket unidentifiedPacket = (UnidentifiedPacket)PacketIdentifier.convertToPacket(message,
                UnidentifiedPacket.class);

        if(unidentifiedPacket == null || !unidentifiedPacket.isValid()) {
            mLogger.warn(String.format("[%s] Invalid packet received: \"%s\".", session.getId(), message));
            return;
        }

        Class<?> packetType = unidentifiedPacket.getType();
        mLogger.info(String.format("Packet Type: %s", packetType.toString()));

        // User does not yet have a username assigned to them.
        if(!userProperties.containsKey(PROPERTY_USERNAME)) {
            if(packetType != UsernameRequestPacket.class) {
                mLogger.warn(String.format("[%s] Unexpected packet received (username not set): \"%s\".",
                        session.getId(), message));
                return;
            }

            UsernameRequestPacket usernameRequestPacket = (UsernameRequestPacket)PacketIdentifier.convertToPacket(
                    message, UsernameRequestPacket.class);

            if(usernameRequestPacket == null || !usernameRequestPacket.isValid()) {
                sendMessage(gson.toJson(new UsernameResponsePacket(false)), session);
                return;
            }

            for(Session userSession : mUsers) {
                if(userSession.getUserProperties().containsKey(PROPERTY_USERNAME)) {
                    if(userSession.getUserProperties().get(PROPERTY_USERNAME).equals(usernameRequestPacket.getUsername())) {
                        sendMessage(gson.toJson(new UsernameResponsePacket(false)), session);
                        return;
                    }
                }
            }

            userProperties.put(PROPERTY_USERNAME, usernameRequestPacket.getUsername());
            sendMessage(gson.toJson(new UsernameResponsePacket(true)), session);

            return;
        }

        if(!userProperties.containsKey(PROPERTY_ROOM)) {
            if(unidentifiedPacket.getType() != ChatroomsRequestPacket.class &&
                    unidentifiedPacket.getType() != JoinChatroomRequestPacket.class) {
                mLogger.warn(String.format("[%s] Unexpected packet received (user not in a room): \"%s\".",
                        session.getId(), message));
                return;
            }

            if(unidentifiedPacket.getType() == ChatroomsRequestPacket.class) {
                ChatroomsResponsePacket chatroomsResponsePacket = new ChatroomsResponsePacket(mChatrooms.toArray(
                        new Chatroom[mChatrooms.size()]));
                sendMessage(gson.toJson(chatroomsResponsePacket), session);
                return;
            }

            if(unidentifiedPacket.getType() == JoinChatroomRequestPacket.class) {
                JoinChatroomRequestPacket joinChatroomRequestPacket = (JoinChatroomRequestPacket)PacketIdentifier.
                        convertToPacket(message, JoinChatroomRequestPacket.class);
                if(joinChatroomRequestPacket == null || !joinChatroomRequestPacket.isValid()) {
                    sendMessage(gson.toJson(new JoinChatroomResponsePacket(false)), session);
                    return;
                }

                for(Chatroom chatroom : mChatrooms) {
                    if(chatroom.getName().equals(joinChatroomRequestPacket.getChatroomName())) {
                        chatroom.addMember(session);
                        userProperties.put(PROPERTY_ROOM, chatroom);
                        sendMessage(gson.toJson(new JoinChatroomResponsePacket(true)), session);
                        return;
                    }
                }

                sendMessage(gson.toJson(new JoinChatroomResponsePacket(false)), session);
            }
        }

        if(unidentifiedPacket.getType() == LeaveChatroomRequestPacket.class) {
            Chatroom chatroom = (Chatroom)session.getUserProperties().get(PROPERTY_ROOM);
            if(chatroom != null && chatroom.containsMember(session)) {
                chatroom.removeMember(session);
                session.getUserProperties().remove(PROPERTY_ROOM);
                sendMessage(gson.toJson(new LeaveChatroomResponsePacket(true)), session);
                return;
            }
            else {
                sendMessage(gson.toJson(new LeaveChatroomResponsePacket(false)), session);
                return;
            }
        }

        if(unidentifiedPacket.getType() == SendMessagePacket.class) {
            SendMessagePacket sendMessagePacket = (SendMessagePacket)PacketIdentifier.convertToPacket(message,
                    SendMessagePacket.class);
            if(sendMessagePacket == null || !sendMessagePacket.isValid()) {
                mLogger.warn(String.format("[%s] Invalid packet received: \"%s\".", session.getId(), message));
                return;
            }

            ReceiveMessagePacket receiveMessagePacket = new ReceiveMessagePacket((String)session.getUserProperties()
                    .get(PROPERTY_USERNAME), sendMessagePacket.getMessage());
            for(Session user : ((Chatroom)userProperties.get(PROPERTY_ROOM)).getMembers()) {
                sendMessage(gson.toJson(receiveMessagePacket), user);
            }
        }
    }

    @OnClose
    public void onClose(Session session) throws IOException {
        mLogger.info(String.format("[%s] closed.", session.getId()));
        if(session.getUserProperties().containsKey(PROPERTY_ROOM)) {
            Chatroom chatroom = (Chatroom)session.getUserProperties().get(PROPERTY_ROOM);
            chatroom.removeMember(session);
        }
        mUsers.remove(session);
    }
}
