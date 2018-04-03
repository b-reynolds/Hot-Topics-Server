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
import java.util.*;

@ServerEndpoint("/chat")
public class HotTopicsEndpoint {

    /** Used for class logging */
    private final Logger mLogger = LoggerFactory.getLogger(ChatEndpoint.class);

    private static TrendManager mTrendManager = new TrendManager();

    /** Synchronized set used to store client sessions */
    private static final Set<Session> mConnectedClients = Collections.synchronizedSet(new HashSet<Session>());
    /** Synchronized set used to store active chatrooms */
    private static Set<Chatroom> mChatrooms = Collections.synchronizedSet(new HashSet<Chatroom>());

    private static final String CLIENT_PROPERTY_STATE = "STATE";
    private static final String CLIENT_PROPERTY_USERNAME = "USERNAME";
    private static final String CLIENT_PROPERTY_CHATROOM = "CHATROOM";


    private enum State { UNREGISTERED, REGISTERED, CHATTING };

    @OnOpen
    public void onOpen(final Session client) {

        if(mChatrooms.isEmpty()) {
            populateChatrooms();
        }

        client.getUserProperties().put(CLIENT_PROPERTY_STATE, State.UNREGISTERED);
        mConnectedClients.add(client);
        mLogger.info(String.format("[%s] Client connected [%s active session(s)].", client.getId(), mConnectedClients.size()));
    }

    @OnMessage
    public void onMessage(final String message, final Session client) {
        mLogger.info(String.format("[%s] Received String : \"%s\".", client.getId(), message));

        for(Session session : mConnectedClients) {
            sendPacket(new ReceiveMessagePacket("Server", message), session);
        }


        mLogger.info(String.format("[%s] Attempting to convert string into a Packet instance...", client.getId()));
        UnidentifiedPacket unidentifiedPacket = PacketIdentifier.convertToPacket(message, UnidentifiedPacket.class);
        if(unidentifiedPacket == null) {
            mLogger.warn(String.format("[%s] Failed to convert string into a valid Packet instance.", client.getId()));
            return;
        }

        Class<?> packetType = unidentifiedPacket.getType();
        mLogger.info(String.format("[%s] Received Packet: [%s].", client.getId(), packetType.getSimpleName()));

        switch((State)client.getUserProperties().get(CLIENT_PROPERTY_STATE)) {
            case UNREGISTERED:
                if(packetType == UsernameRequestPacket.class)
                    processUsernameRequest(client, PacketIdentifier.convertToPacket(message, UsernameRequestPacket.class));
                else
                    mLogger.warn(String.format("[%s] Invalid Packet received for client's active state", client.getId()));
                break;
            case REGISTERED:
                if(packetType == ChatroomsRequestPacket.class)
                    processChatroomsRequest(client, PacketIdentifier.convertToPacket(message, ChatroomsRequestPacket.class));
                else if(packetType == JoinChatroomRequestPacket.class)
                    processJoinChatroomRequest(client, PacketIdentifier.convertToPacket(message, JoinChatroomRequestPacket.class));
                else
                    mLogger.warn(String.format("[%s] Invalid Packet received for client's active state", client.getId()));
                break;
            case CHATTING:
                if(packetType == SendMessagePacket.class)
                    processSendMessageRequest(client, PacketIdentifier.convertToPacket(message, SendMessagePacket.class));
                else if(packetType == LeaveChatroomRequestPacket.class)
                    processLeaveChatroomRequest(client, PacketIdentifier.convertToPacket(message, LeaveChatroomRequestPacket.class));
                else
                    mLogger.warn(String.format("[%s] Invalid Packet received for client's active state", client.getId()));
                break;
        }
    }

    @OnClose
    public void onClose(final Session client) {
        if(client.getUserProperties().get(CLIENT_PROPERTY_STATE) == State.CHATTING) {
            ((Chatroom)client.getUserProperties().get(CLIENT_PROPERTY_CHATROOM)).removeMember(client);
        }
        mConnectedClients.remove(client);
        mLogger.info(String.format("Client disconnected [%s] (%s active sessions).", client.getId(), mConnectedClients.size()));
    }

    private void populateChatrooms() {
        mChatrooms.clear();
        Trend[] currentTrends = mTrendManager.getTrends().getTrends();
        for(int i = 0; i < (currentTrends.length < 10 ? currentTrends.length : 10); i++) {
            mChatrooms.add(new Chatroom(currentTrends[i].getName()));
        }
    }

    private void sendPacket(final Packet packet, final Session client) {
        mLogger.info(String.format("[%s] Sending [%s].", client.getId(), packet.getClass().getSimpleName()));
        try {
            client.getBasicRemote().sendText(packet.toString());
        } catch (IOException e) {
            mLogger.warn(String.format("[%s] Failed to send Packet", client.getId()));
        }
    }

    private void processUsernameRequest(Session client, UsernameRequestPacket packet) {
        mLogger.info(String.format("[%s] Processing [%s]...", client.getId(), UsernameRequestPacket.class.getSimpleName()));
        if(packet == null) {
            mLogger.warn(String.format("[%s] Invalid [%s] received.", client.getId(), UsernameRequestPacket.class.getSimpleName()));
            sendPacket(new UsernameResponsePacket(false), client);
            return;
        }

        mLogger.info(String.format("[%s] Checking if username \"%s\" is unique...", client.getId(), packet.getUsername()));
        for(Session connectedClient : mConnectedClients) {
            if(Objects.equals(connectedClient.getId(), client.getId())) {
                continue;
            }

            Map<String, Object> clientProperties = connectedClient.getUserProperties();
            if(!clientProperties.containsKey(CLIENT_PROPERTY_USERNAME)) {
                continue;
            }

            String clientsUsername = (String)clientProperties.get(CLIENT_PROPERTY_USERNAME);
            if(packet.getUsername().toUpperCase().equals(clientsUsername.toUpperCase())) {
                mLogger.info(String.format("[%s] Requested username has already been taken by client [%s].", client.getId(), connectedClient.getId()));
                sendPacket(new UsernameResponsePacket(false), client);
                return;
            }
        }

        client.getUserProperties().put(CLIENT_PROPERTY_USERNAME, packet.getUsername());
        client.getUserProperties().put(CLIENT_PROPERTY_STATE, State.REGISTERED);

        mLogger.info(String.format("[%s] Username successfully assigned.", client.getId()));

        sendPacket(new UsernameResponsePacket(true), client);
    }

    private void processChatroomsRequest(Session client, ChatroomsRequestPacket packet) {
        mLogger.info(String.format("[%s] Processing [%s]...", client.getId(), ChatroomsRequestPacket.class.getSimpleName()));
        if(packet == null) {
            mLogger.warn(String.format("[%s] Invalid [%s] received.", client.getId(), ChatroomsRequestPacket.class.getSimpleName()));
            return;
        }

        ChatroomsResponsePacket chatroomsResponsePacket = new ChatroomsResponsePacket(mChatrooms.toArray(new Chatroom[mChatrooms.size()]));
        sendPacket(chatroomsResponsePacket, client);
    }

    private void processJoinChatroomRequest(Session client, JoinChatroomRequestPacket packet) {
        mLogger.info(String.format("[%s] Processing [%s]...", client.getId(), JoinChatroomRequestPacket.class.getSimpleName()));
        if(packet == null) {
            mLogger.warn(String.format("[%s] Invalid [%s] received.", client.getId(), JoinChatroomRequestPacket.class.getSimpleName()));
            return;
        }

        for(Chatroom chatroom : mChatrooms) {
            if(chatroom.getName().equals(packet.getChatroomName())) {
                client.getUserProperties().put(CLIENT_PROPERTY_STATE, State.CHATTING);
                client.getUserProperties().put(CLIENT_PROPERTY_CHATROOM, chatroom);
                chatroom.addMember(client);
                sendPacket(new JoinChatroomResponsePacket(true), client);
                mLogger.info(String.format("[%s] Entered chatroom [%s]...", client.getId(), chatroom.getName()));
                return;
            }
        }

        sendPacket(new JoinChatroomResponsePacket(false), client);
    }

    private void processSendMessageRequest(Session client, SendMessagePacket packet) {
        mLogger.info(String.format("[%s] Processing [%s]...", client.getId(), SendMessagePacket.class.getSimpleName()));
        if(packet == null) {
            mLogger.warn(String.format("[%s] Invalid [%s] received.", client.getId(), SendMessagePacket.class.getSimpleName()));
            return;
        }

        mLogger.info(String.format("[%s] Sending message to all clients in room [%s]...", client.getId(), ((Chatroom)client.getUserProperties().get(CLIENT_PROPERTY_CHATROOM)).getName()));
        ReceiveMessagePacket receiveMessagePacket = new ReceiveMessagePacket((String)client.getUserProperties().get(CLIENT_PROPERTY_USERNAME), packet.getMessage());
        for(Session clientInRoom : ((Chatroom)client.getUserProperties().get(CLIENT_PROPERTY_CHATROOM)).getMembers()) {
            sendPacket(receiveMessagePacket, clientInRoom);
        }
    }

    private void processLeaveChatroomRequest(Session client, LeaveChatroomRequestPacket packet) {
        mLogger.info(String.format("[%s] Processing [%s]...", client.getId(), LeaveChatroomRequestPacket.class.getSimpleName()));
        if(packet == null) {
            mLogger.warn(String.format("[%s] Invalid [%s] received.", client.getId(), LeaveChatroomRequestPacket.class.getSimpleName()));
            return;
        }

        Chatroom chatroom = (Chatroom)client.getUserProperties().get(CLIENT_PROPERTY_CHATROOM);
        if(chatroom != null && chatroom.containsMember(client)) {
            mLogger.info(String.format("[%s] Removing client from room [%s]...", client.getId(), chatroom.getName()));
            chatroom.removeMember(client);
            client.getUserProperties().put(CLIENT_PROPERTY_CHATROOM, null);
            client.getUserProperties().put(CLIENT_PROPERTY_STATE, State.REGISTERED);
            sendPacket(new LeaveChatroomResponsePacket(true), client);
            return;
        }

        sendPacket(new LeaveChatroomResponsePacket(false), client);
    }
}
