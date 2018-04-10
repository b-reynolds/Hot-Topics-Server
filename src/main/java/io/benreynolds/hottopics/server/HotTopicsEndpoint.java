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
    private static final Logger mLogger = LoggerFactory.getLogger(HotTopicsEndpoint.class);

    private static final TrendManager mTrendManager = new TrendManager();

    /** Synchronized set used to store client sessions */
    private static final Set<Session> mConnectedClients = Collections.synchronizedSet(new HashSet<Session>());
    /** Synchronized set used to store active chatrooms */
    private static List<Chatroom> mChatrooms = Collections.synchronizedList(new ArrayList<Chatroom>());
    /** Synchronized map used to store messages that have been sent in a chatroom */
    private static Map<Chatroom, LinkedList<ReceiveMessagePacket>> mMessageCache = Collections.synchronizedMap(new HashMap<Chatroom, LinkedList<ReceiveMessagePacket>>());

    private static int MESSAGES_TO_CACHE = 50;

    private static final String CLIENT_PROPERTY_STATE = "STATE";
    private static final String CLIENT_PROPERTY_USERNAME = "USERNAME";
    private static final String CLIENT_PROPERTY_CHATROOM = "CHATROOM";

    private enum State { UNREGISTERED, REGISTERED, CHATTING }

    private static Thread tUpdateTrends;

    @OnOpen
    public void onOpen(final Session client) {

        if(tUpdateTrends == null) {
            tUpdateTrends = new Thread(new UpdateTrendsTask());
            tUpdateTrends.start();
        }

        client.getUserProperties().put(CLIENT_PROPERTY_STATE, State.UNREGISTERED);
        mConnectedClients.add(client);
        mLogger.info(String.format("[%s] Client connected [%s active session(s)].", client.getId(), mConnectedClients.size()));
    }

    @OnMessage
    public void onMessage(final String message, final Session client) {
        mLogger.info(String.format("[%s] Received String : \"%s\".", client.getId(), message));

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
        ArrayList<Trend> currentTrends = mTrendManager.getTrends();
        for(int i = 0; i < currentTrends.size(); i++) {
            mChatrooms.add(new Chatroom(currentTrends.get(i).getName()));
            System.out.println(i + ": " + mChatrooms.get(i).getName());
        }
    }

    private static void sendPacket(final Packet packet, final Session client) {
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

                sendUpdatedChatroomsList();

                // Update chatting users with the amount of users in the room
                ChatroomUserCountUpdatePacket chatroomUserCountUpdatePacket = new ChatroomUserCountUpdatePacket(chatroom.getMembers().size());
                for(Session chattingUser : chatroom.getMembers()) {
                    sendPacket(chatroomUserCountUpdatePacket, chattingUser);
                }

                LinkedList<ReceiveMessagePacket> roomMessages = mMessageCache.get(chatroom);
                for(ReceiveMessagePacket receiveMessagePacket : roomMessages) {
                    if(receiveMessagePacket != null) {
                        sendPacket(receiveMessagePacket, client);
                    }
                }

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

        Chatroom chatroom = ((Chatroom)client.getUserProperties().get(CLIENT_PROPERTY_CHATROOM));
        chatroom.addMessage(receiveMessagePacket);

        for(Session clientInRoom : chatroom.getMembers()) {
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
            sendUpdatedChatroomsList();

            // Update chatting users with the amount of users in the room
            ChatroomUserCountUpdatePacket chatroomUserCountUpdatePacket = new ChatroomUserCountUpdatePacket(chatroom.getMembers().size());
            for(Session chattingUser : chatroom.getMembers()) {
                sendPacket(chatroomUserCountUpdatePacket, chattingUser);
            }

            return;
        }

        sendPacket(new LeaveChatroomResponsePacket(false), client);
    }


    private static void sendUpdatedChatroomsList() {
        // Resend the chatroom list to users in the REGISTERED state so that the amount of users in each room is updated
        ChatroomsResponsePacket chatroomsResponsePacket = new ChatroomsResponsePacket(mChatrooms.toArray(new Chatroom[mChatrooms.size()]));
        for(Session connectedClient : mConnectedClients) {
            if(connectedClient.getUserProperties().get(CLIENT_PROPERTY_STATE) == State.REGISTERED) {
                sendPacket(chatroomsResponsePacket, connectedClient);
            }
        }
    }

    private static void refreshChatroomList() {

        String methodName = new Object() {}
                .getClass()
                .getEnclosingMethod()
                .getName();

        mLogger.info(String.format("[%s]: Retrieving latest trend data and updating chatrooms...", methodName));

        // Request an updated list of currently trending events
        ArrayList<Trend> trendingTopics = mTrendManager.getTrends();

        mLogger.info(String.format("[%s]: Retrieved %s trends.", methodName, trendingTopics.size()));

        // If this is the first time a request has been made, populate the chatrooms list with the retrieved trends.
        if(mChatrooms.isEmpty()) {
            mLogger.info(String.format("[%s]: There are currently no chatrooms, adding chatrooms for all trends...", methodName));
            for(Trend trend : trendingTopics) {
                mChatrooms.add(new Chatroom(trend.getName()));
            }
            mLogger.info(String.format("[%s]: Added %s chatrooms.", methodName, mChatrooms.size()));
        }
        else {

            // Remove chatrooms that are no longer trending and are empty
            mLogger.info(String.format("[%s]: Checking for outdated and empty chatrooms...", methodName));
            for (int i = 0; i < mChatrooms.size(); i++) {
                boolean chatroomStillTrending = false;
                for (Trend trendingTopic : trendingTopics) {
                    if (mChatrooms.get(i).getName().equals(trendingTopic.getName())) {
                        chatroomStillTrending = true;
                        break;
                    }
                }

                if (!chatroomStillTrending && mChatrooms.get(i).getSize() == 0) {
                    mLogger.info(String.format("[%s]: Removing chatroom \"%s\"...", methodName, mChatrooms.get(i).getName()));
                    mChatrooms.remove(i);
                }
            }

            // Add chatrooms for new trends
            mLogger.info(String.format("[%s]: Creating chatrooms for new trends...", methodName));
            for (Trend trendingTopic : trendingTopics) {
                boolean chatroomExistsForTrend = false;
                for (Chatroom chatroom : mChatrooms) {
                    if (chatroom.getName().equals(trendingTopic.getName())) {
                        chatroomExistsForTrend = true;
                        break;
                    }
                }

                if (!chatroomExistsForTrend) {
                    mLogger.info(String.format("[%s]: Creating chatroom for trend \"%s\"...", methodName, trendingTopic.getName()));
                    mChatrooms.add(new Chatroom(trendingTopic.getName()));
                }
            }
        }

        sendUpdatedChatroomsList();
    }


    // IF TREND_UPDATE_RATE == ELAPSED
    //     GET NEW TRENDS
    //     UPDATE CHATROOM LIST
    //     PUSH NEW CHATROOMS
    //



    private static class UpdateTrendsTask implements Runnable {

        private static int MILLISECONDS_IN_SECOND = 1000;
        private static int SECONDS_IN_MINUTE = 60;

        private static int TREND_UPDATE_RATE_MINUTES = 1;


        private static Timer mUpdateTimer;

        @Override
        public void run() {

            refreshChatroomList();

            mUpdateTimer = new Timer(SECONDS_IN_MINUTE * TREND_UPDATE_RATE_MINUTES);
            mUpdateTimer.start();

            while(!Thread.interrupted()) {

                mLogger.info(String.format("Time Before Trend Update: %s minutes.", Math.round(mUpdateTimer.getTimeRemaining() / SECONDS_IN_MINUTE)));

                if(mUpdateTimer.hasElapsed()) {
                    refreshChatroomList();
                    mUpdateTimer.start();
                }

                try {
                    Thread.sleep(MILLISECONDS_IN_SECOND * SECONDS_IN_MINUTE);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }


        }


    }

}
