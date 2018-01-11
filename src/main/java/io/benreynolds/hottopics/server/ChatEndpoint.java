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
        mLogger.info(String.format("Packet Received: %s", message));
        if(mChatrooms.isEmpty()) {
            Trend[] currentTrends = mTrendManager.getTrends().getTrends();
            for(int i = 0; i < (currentTrends.length < 10 ? currentTrends.length : 10); i++) {
                mChatrooms.add(new Chatroom(currentTrends[i].getName()));
            }
        }

        Map<String, Object> userProperties = session.getUserProperties();

        UnidentifiedPacket unidentifiedPacket = PacketIdentifier.convertToPacket(message, UnidentifiedPacket.class);

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

            UsernameRequestPacket usernameRequestPacket = PacketIdentifier.convertToPacket(message,
                    UsernameRequestPacket.class);

            if(usernameRequestPacket == null || !usernameRequestPacket.isValid()) {
                sendMessage(new UsernameResponsePacket(false).toString(), session);
                mLogger.info(String.format("Sent Packet to %s: %s", session.getUserProperties().get(PROPERTY_USERNAME), new UsernameResponsePacket(false).toString()));
                return;
            }

            for(Session userSession : mUsers) {
                if(userSession.getUserProperties().containsKey(PROPERTY_USERNAME)) {
                    if(userSession.getUserProperties().get(PROPERTY_USERNAME).equals(usernameRequestPacket.getUsername())) {
                        sendMessage(new UsernameResponsePacket(false).toString(), session);
                        mLogger.info(String.format("Sent Packet to %s: %s", session.getUserProperties().get(PROPERTY_USERNAME), new UsernameResponsePacket(false).toString()));
                        return;
                    }
                }
            }

            userProperties.put(PROPERTY_USERNAME, usernameRequestPacket.getUsername());
            sendMessage(new UsernameResponsePacket(true).toString(), session);
            mLogger.info(String.format("Sent Packet to %s: %s", session.getUserProperties().get(PROPERTY_USERNAME), new UsernameResponsePacket(true).toString()));
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
                sendMessage(chatroomsResponsePacket.toString(), session);
                mLogger.info(String.format("Sent Packet to %s: %s", session.getUserProperties().get(PROPERTY_USERNAME), chatroomsResponsePacket.toString()));
                return;
            }

            if(unidentifiedPacket.getType() == JoinChatroomRequestPacket.class) {
                JoinChatroomRequestPacket joinChatroomRequestPacket = PacketIdentifier.convertToPacket(message,
                        JoinChatroomRequestPacket.class);
                if(joinChatroomRequestPacket == null || !joinChatroomRequestPacket.isValid()) {
                    sendMessage(new JoinChatroomResponsePacket(false).toString(), session);
                    mLogger.info(String.format("Sent Packet to %s: %s", session.getUserProperties().get(PROPERTY_USERNAME), new JoinChatroomResponsePacket(false).toString()));
                    return;
                }

                for(Chatroom chatroom : mChatrooms) {
                    if(chatroom.getName().equals(joinChatroomRequestPacket.getChatroomName())) {
                        chatroom.addMember(session);
                        userProperties.put(PROPERTY_ROOM, chatroom);
                        sendMessage(new JoinChatroomResponsePacket(true).toString(), session);
                        mLogger.info(String.format("Sent Packet to %s: %s", session.getUserProperties().get(PROPERTY_USERNAME), new JoinChatroomResponsePacket(true).toString()));
                        return;
                    }
                }

                sendMessage(new JoinChatroomResponsePacket(false).toString(), session);
                mLogger.info(String.format("Sent Packet to %s: %s", session.getUserProperties().get(PROPERTY_USERNAME), new JoinChatroomResponsePacket(false).toString()));
            }
        }

        if(unidentifiedPacket.getType() == LeaveChatroomRequestPacket.class) {
            Chatroom chatroom = (Chatroom)session.getUserProperties().get(PROPERTY_ROOM);
            if(chatroom != null && chatroom.containsMember(session)) {
                chatroom.removeMember(session);
                session.getUserProperties().remove(PROPERTY_ROOM);
                sendMessage(new LeaveChatroomResponsePacket(true).toString(), session);
                mLogger.info(String.format("Sent Packet to %s: %s", session.getUserProperties().get(PROPERTY_USERNAME), new LeaveChatroomResponsePacket(true).toString()));
                return;
            }
            else {
                sendMessage(new LeaveChatroomResponsePacket(false).toString(), session);
                mLogger.info(String.format("Sent Packet to %s: %s", session.getUserProperties().get(PROPERTY_USERNAME), new LeaveChatroomResponsePacket(false).toString()));
                return;
            }
        }

        if(unidentifiedPacket.getType() == SendMessagePacket.class) {
            SendMessagePacket sendMessagePacket = PacketIdentifier.convertToPacket(message, SendMessagePacket.class);
            if(sendMessagePacket == null || !sendMessagePacket.isValid()) {
                mLogger.warn(String.format("[%s] Invalid packet received: \"%s\".", session.getId(), message));
                return;
            }

            ReceiveMessagePacket receiveMessagePacket = new ReceiveMessagePacket((String)session.getUserProperties()
                    .get(PROPERTY_USERNAME), sendMessagePacket.getMessage());
            for(Session user : ((Chatroom)userProperties.get(PROPERTY_ROOM)).getMembers()) {
                sendMessage(receiveMessagePacket.toString(), user);
                mLogger.info(String.format("Sent Packet to %s: %s", session.getUserProperties().get(PROPERTY_USERNAME), receiveMessagePacket.toString()));
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
