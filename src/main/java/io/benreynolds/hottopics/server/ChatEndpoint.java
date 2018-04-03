package io.benreynolds.hottopics.server;

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

    private static final Set<Session> mSessions = Collections.synchronizedSet(new HashSet<Session>());

    private static TrendManager mTrendManager = new TrendManager();

    private static final String PROPERTY_USERNAME = "Username";
    private static final String PROPERTY_ROOM = "Room";

    private static final String PROPERTY_STATE = "State";

    private static final int STATE_CONNECTED = 0;
    private static final int STATE_NO_ROOM = 1;
    private static final int STATE_IN_ROOM = 2;

    private static Set<Chatroom> mChatrooms = Collections.synchronizedSet(new HashSet<Chatroom>());

    private boolean sendPacket(final Packet packet, final Session session) {
        mLogger.info(String.format("Sending Packet to [%s]: \"%s\".", session.getId(), packet.toString()));
        try {
            session.getBasicRemote().sendText(packet.toString());
        } catch (IOException e) {
            mLogger.warn("Sending Packet failed.");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @OnOpen
    public void onOpen(Session session) {
        mSessions.add(session);
        mLogger.info(String.format("Session opened [%s] (%s active sessions).", session.getId(), mSessions.size()));
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        mLogger.info(String.format("Received message from [%s]: \"%s\".", session.getId(), message));

        UnidentifiedPacket unidentifiedPacket = PacketIdentifier.convertToPacket(message, UnidentifiedPacket.class);
        if(unidentifiedPacket == null) {
            mLogger.warn(String.format("Invalid Packet received: \"%s\".", message));
            return;
        }

        Class<?> packetType = unidentifiedPacket.getType();
        mLogger.info(String.format("Received Packet: \"%s\".", packetType.toString()));

        int sessionState = (int)session.getUserProperties().get(PROPERTY_STATE);

        if(sessionState == STATE_CONNECTED) {
            if(packetType != UsernameRequestPacket.class) {
                mLogger.warn(String.format("Unexpected Packet received, expected \"%s\", received \"%s\".",
                        UsernameRequestPacket.class.getSimpleName(), packetType.getSimpleName()));
                return;
            }

            // ...

            UsernameRequestPacket requestPacket = PacketIdentifier.convertToPacket(message, UsernameRequestPacket.class);
            if(requestPacket == null || !requestPacket.isValid()) {
                sendPacket(new UsernameResponsePacket(false), session);
                return;
            }

        }

        Map<String, Object> userProperties = session.getUserProperties();

        // User does not yet have a username assigned to them.
        if(!userProperties.containsKey(PROPERTY_USERNAME)) {
            if(packetType != UsernameRequestPacket.class) {
                mLogger.warn(String.format("Unexpected Packet received, expected \"%s\", received \"%s\".",
                        UsernameRequestPacket.class.getSimpleName(), packetType.getSimpleName()));
                return;
            }

            UsernameRequestPacket requestPacket = PacketIdentifier.convertToPacket(message, UsernameRequestPacket.class);
            if(requestPacket == null || !requestPacket.isValid()) {
                // Username does not pass validation rules.
                sendPacket(new UsernameResponsePacket(false), session);
                return;
            }

            for(Session activeSession : mSessions) {
                Map<String, Object> activeSessionProperties = activeSession.getUserProperties();
                if(!activeSessionProperties.containsKey(PROPERTY_USERNAME)) {
                    continue;
                }

                String sessionUsername = (String)activeSessionProperties.get(PROPERTY_USERNAME);
                if(requestPacket.getUsername().toUpperCase().equals(sessionUsername.toUpperCase())) {
                    sendPacket(new UsernameResponsePacket(false), session);
                    return;
                }
            }

            userProperties.put(PROPERTY_USERNAME, requestPacket.getUsername());
            userProperties.put(PROPERTY_STATE, STATE_NO_ROOM);

            sendPacket(new UsernameResponsePacket(true), session);

            return;
        }

        if(!userProperties.containsKey(PROPERTY_ROOM)) {
            if(unidentifiedPacket.getType() != ChatroomsRequestPacket.class &&
                    unidentifiedPacket.getType() != JoinChatroomRequestPacket.class) {
                mLogger.warn(String.format("Unexpected Packet received, expected \"%s\" or \"%s\", received \"%s\".",
                    ChatroomsRequestPacket.class.getSimpleName(), JoinChatroomRequestPacket.class.getSimpleName(),
                    packetType.getSimpleName()));
                return;
            }

            if(unidentifiedPacket.getType() == ChatroomsRequestPacket.class) {
                // This is the first time a chatroom list has been requested, build one using the twitter API.
                if(mChatrooms.isEmpty()) {
                    Trend[] currentTrends = mTrendManager.getTrends().getTrends();
                    for(int i = 0; i < (currentTrends.length < 10 ? currentTrends.length : 10); i++) {
                        mChatrooms.add(new Chatroom(currentTrends[i].getName()));
                    }
                }

                ChatroomsResponsePacket chatroomsResponsePacket = new ChatroomsResponsePacket(mChatrooms.toArray(
                        new Chatroom[mChatrooms.size()]));
                sendPacket(chatroomsResponsePacket, session);
                return;
            }

            if(unidentifiedPacket.getType() == JoinChatroomRequestPacket.class) {
                JoinChatroomRequestPacket joinChatroomRequestPacket = PacketIdentifier.convertToPacket(message,
                        JoinChatroomRequestPacket.class);
                // TODO: packets are already valid if not null
                if(joinChatroomRequestPacket == null || !joinChatroomRequestPacket.isValid()) {
                    sendPacket(new JoinChatroomResponsePacket(false), session);
                    return;
                }

                for(Chatroom chatroom : mChatrooms) {
                    if(chatroom.getName().equals(joinChatroomRequestPacket.getChatroomName())) {
                        chatroom.addMember(session);
                        userProperties.put(PROPERTY_ROOM, chatroom);
                        sendPacket(new JoinChatroomResponsePacket(true), session);
                        return;
                    }
                }

                sendPacket(new JoinChatroomResponsePacket(false), session);
            }
        }

        if(unidentifiedPacket.getType() == LeaveChatroomRequestPacket.class) {
            Chatroom chatroom = (Chatroom)session.getUserProperties().get(PROPERTY_ROOM);
            if(chatroom != null && chatroom.containsMember(session)) {
                chatroom.removeMember(session);
                session.getUserProperties().remove(PROPERTY_ROOM);
                sendPacket(new LeaveChatroomResponsePacket(true), session);
                return;
            }
            else {
                sendPacket(new LeaveChatroomResponsePacket(false), session);
                return;
            }
        }

        if(unidentifiedPacket.getType() == SendMessagePacket.class) {
            SendMessagePacket sendMessagePacket = PacketIdentifier.convertToPacket(message, SendMessagePacket.class);
            if(sendMessagePacket == null || !sendMessagePacket.isValid()) {
                mLogger.warn("Message was not a valid Packet.");
                return;
            }

            ReceiveMessagePacket receiveMessagePacket = new ReceiveMessagePacket((String)session.getUserProperties()
                    .get(PROPERTY_USERNAME), sendMessagePacket.getMessage());
            for(Session sessionInRoom : ((Chatroom)userProperties.get(PROPERTY_ROOM)).getMembers()) {
                sendPacket(receiveMessagePacket, sessionInRoom);
            }
        }
    }

    @OnClose
    public void onClose(Session session) throws IOException {
        mLogger.info(String.format("Closing session [%s]...", session.getId()));
        if(session.getUserProperties().containsKey(PROPERTY_ROOM)) {
            Chatroom chatroom = (Chatroom)session.getUserProperties().get(PROPERTY_ROOM);
            chatroom.removeMember(session);
            mLogger.info(String.format("Removed session [%s] from chatroom [%s]...", session.getId(),
                    chatroom.getName()));
        }
        mSessions.remove(session);
        mLogger.info(String.format("Session closed [%s] (%s active sessions).", session.getId(), mSessions.size()));
    }

}
