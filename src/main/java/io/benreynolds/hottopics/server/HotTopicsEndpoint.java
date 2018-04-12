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
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@ServerEndpoint("/chat")
public class HotTopicsEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotTopicsEndpoint.class);
    private static final TrendManager TREND_MANAGER = new TrendManager();
    private static final Map<Session, Client> CONNECTED_CLIENTS = Collections.synchronizedMap(new HashMap<>());
    private static final List<Chatroom> CHATROOMS = Collections.synchronizedList(new ArrayList<>());
    private static final List<PacketHandler> PACKET_HANDLERS = Collections.synchronizedList(Arrays.asList(
            new SendMessagePacketHandler(),
            new UsernameRequestPacketHandler(),
            new ChatroomsRequestPacketHandler(),
            new JoinChatroomRequestPacketHandler(),
            new LeaveChatroomRequestPacketHandler()));

    private static Thread tUpdateAvailableChatrooms;
    private static Thread tRemoveInactiveConnections;


    public HotTopicsEndpoint() {
        if(tUpdateAvailableChatrooms == null || !tUpdateAvailableChatrooms.isAlive()) {
            tUpdateAvailableChatrooms = new Thread(new UpdateAvailableChatroomsTask());
            tUpdateAvailableChatrooms.start();
        }
        if(tRemoveInactiveConnections == null || !tRemoveInactiveConnections.isAlive()) {
            tRemoveInactiveConnections = new Thread(new RemoveInactiveConnectionsTask());
            tRemoveInactiveConnections.start();
        }
    }

    @OnOpen
    public static void onOpen(final Session session) {
        LOGGER.info(String.format("[%s] Opened.", session.getId()));
        CONNECTED_CLIENTS.put(session, new Client(session));
    }

    @OnMessage
    public static void onMessage(final String message, final Session session) {
        // Attempt to de-serialize the received message into a valid Packet instance.
        LOGGER.info(String.format("[%s] Received message: \"%s\".", session.getId(), message));
        LOGGER.info(String.format("[%s] Attempting to de-serialize message into a Packet...", session.getId()));
        UnidentifiedPacket unidentifiedPacket = PacketIdentifier.convertToPacket(message, UnidentifiedPacket.class);
        if(unidentifiedPacket == null) {
            LOGGER.warn(String.format("[%s] Failed to convert message into a valid Packet instance.", session.getId()));
            return;
        }

        Class<?> packetType = unidentifiedPacket.getType();
        LOGGER.info(String.format("[%s] Successfully identified message as a valid packet of type \"%s\".", session.getId(), packetType.getSimpleName()));

        // Handle the Packet.
        Client sender = CONNECTED_CLIENTS.get(session);
        sender.setLastMessageTime(LocalTime.now());
        if(packetType == AcknowledgementResponsePacket.class) {
            return;
        }

        for(PacketHandler packetHandler : PACKET_HANDLERS) {
            if(packetHandler.getType().equals(packetType)) {
                if(packetHandler.getRequiredStates().contains(sender.getState())) {
                    packetHandler.handlePacket(PacketIdentifier.convertToPacket(message, packetHandler.getType()), sender, CONNECTED_CLIENTS, CHATROOMS);
                }
                else {
                    LOGGER.warn(String.format("[%s] Unexpected packet received for client's current state.", session.getId()));
                }
            }
        }
    }

    @OnClose
    public static void onClose(final Session session) {
        LOGGER.info(String.format("[%s] Closed.", session.getId()));
        disconnectClient(CONNECTED_CLIENTS.get(session));
    }

    static void disconnectClient(Client client) {
        if(client.getChatroom() != null) {
            Chatroom clientChatroom = client.getChatroom();
            clientChatroom.removeClient(client);

            sendUpdatedRoomUserCountToClientsInRoom(clientChatroom);
            sendUpdatedChatroomsListToClients();
        }

        CONNECTED_CLIENTS.remove(client.getSession());

        if(client.getSession().isOpen()) {
            try {
                client.getSession().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static void sendUpdatedChatroomsListToClients() {
        // Resend the chatroom list to clients in the ROOM_LIST state.
        ChatroomsResponsePacket chatroomsResponsePacket = new ChatroomsResponsePacket(CHATROOMS.toArray(new Chatroom[CHATROOMS.size()]));
        for(Client client : CONNECTED_CLIENTS.values()) {
            if(client.getState() == Client.State.ROOM_LIST) {
                client.sendPacket(chatroomsResponsePacket);
            }
        }
    }

    static void sendUpdatedRoomUserCountToClientsInRoom(final Chatroom chatroom) {
        ChatroomUserCountUpdatePacket chatroomUserCountUpdatePacket = new ChatroomUserCountUpdatePacket(chatroom.getClients().size());
        for(Client client : chatroom.getClients()) {
            client.sendPacket(chatroomUserCountUpdatePacket);
        }
    }

    static void retrieveTrendsAndRefreshChatrooms() {
        String methodName = new Object() {}
            .getClass()
            .getEnclosingMethod()
            .getName();

        LOGGER.info(String.format("[%s]: Retrieving latest trend data and updating chatrooms...", methodName));

        // Request an updated list of currently trending events
        ArrayList<Trend> trendingTopics = TREND_MANAGER.getTrends();

        LOGGER.info(String.format("[%s]: Retrieved %s trends.", methodName, trendingTopics.size()));

        // If this is the first time a request has been made, populate the chatrooms list with the retrieved trends.
        if(CHATROOMS.isEmpty()) {
            LOGGER.info(String.format("[%s]: There are currently no chatrooms, adding chatrooms for all trends...", methodName));
            for(Trend trend : trendingTopics) {
                CHATROOMS.add(new Chatroom(trend.getName()));
            }
            LOGGER.info(String.format("[%s]: Added %s chatrooms.", methodName, CHATROOMS.size()));
        }
        else {
            // Remove chatrooms that are no longer trending and are empty
            LOGGER.info(String.format("[%s]: Checking for outdated and empty chatrooms...", methodName));
            for (int i = 0; i < CHATROOMS.size(); i++) {
                boolean chatroomStillTrending = false;
                for (Trend trendingTopic : trendingTopics) {
                    if (CHATROOMS.get(i).getName().equals(trendingTopic.getName())) {
                        chatroomStillTrending = true;
                        break;
                    }
                }

                if (!chatroomStillTrending && CHATROOMS.get(i).getSize() == 0) {
                    LOGGER.info(String.format("[%s]: Removing chatroom \"%s\"...", methodName, CHATROOMS.get(i).getName()));
                    CHATROOMS.remove(i);
                }
            }

            // Add chatrooms for new trends
            LOGGER.info(String.format("[%s]: Creating chatrooms for new trends...", methodName));
            for (Trend trendingTopic : trendingTopics) {
                boolean chatroomExistsForTrend = false;
                for (Chatroom chatroom : CHATROOMS) {
                    if (chatroom.getName().equals(trendingTopic.getName())) {
                        chatroomExistsForTrend = true;
                        break;
                    }
                }

                if (!chatroomExistsForTrend) {
                    LOGGER.info(String.format("[%s]: Creating chatroom for trend \"%s\"...", methodName, trendingTopic.getName()));
                    CHATROOMS.add(new Chatroom(trendingTopic.getName()));
                }
            }
        }

        sendUpdatedChatroomsListToClients();
    }

    private static class UpdateAvailableChatroomsTask implements Runnable {

        private static final int MILLISECONDS_IN_SECOND = 1000;
        private static final int SECONDS_IN_MINUTE = 60;
        private static final int TREND_UPDATE_RATE_MINUTES = 5;

        private static Timer mUpdateTimer;

        @Override
        public void run() {

            retrieveTrendsAndRefreshChatrooms();

            mUpdateTimer = new Timer(SECONDS_IN_MINUTE * TREND_UPDATE_RATE_MINUTES);
            mUpdateTimer.start();

            while(!Thread.interrupted()) {

                LOGGER.info(String.format("Time before next trend/chatrooms update: %s minutes.", Math.round(mUpdateTimer.getTimeRemaining() / SECONDS_IN_MINUTE)));

                if(mUpdateTimer.hasElapsed()) {
                    retrieveTrendsAndRefreshChatrooms();
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

    private static class RemoveInactiveConnectionsTask implements Runnable {

        private static long TIME_BEFORE_ACK_REQUIRED_MINS = 1;
        private static long TIME_BEFORE_ACK_FAILED_MINS = 1;

    @Override
    public void run() {

        LinkedList<Client> clientsToDisconnect = new LinkedList<>();

        while(true) {

            LOGGER.info(String.format("[%s]: Attempting to remove inactive connections...", getClass().getSimpleName()));

            LocalTime currentTime = LocalTime.now();
            for (Client client : CONNECTED_CLIENTS.values()) {
                LocalTime lastMessageTime = client.getLastMessageTime();
                if (client.requiresAcknowledgementRequest()) {
                    LocalTime acknowledgementRequestTime = client.getTimeAcknowledgementRequestWasSent();
                    if (Duration.between(lastMessageTime, acknowledgementRequestTime).toMillis() > TimeUnit.MINUTES.toMillis(TIME_BEFORE_ACK_FAILED_MINS)) {
                        clientsToDisconnect.add(client);
                        continue;
                    }
                }

                if (Duration.between(lastMessageTime, currentTime).toMillis() > TimeUnit.MINUTES.toMillis(TIME_BEFORE_ACK_REQUIRED_MINS)) {
                    client.setRequiresAcknowledgementRequest(true);
                    client.setTimeAcknowledgementRequestWasSent(currentTime);
                    client.sendPacket(new AcknowledgementRequestPacket());
                    LOGGER.info(String.format("[%s]: Client \"%s\" was sent an acknowledgement request.", getClass().getSimpleName(), client.getSession().getId()));
                }
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            while(!clientsToDisconnect.isEmpty()) {
                Client clientToDisconnect = clientsToDisconnect.poll();
                disconnectClient(clientToDisconnect);
                LOGGER.info(String.format("[%s]: Disconnected client \"%s\"", getClass().getSimpleName(), clientToDisconnect.getSession().getId()));
            }

        }

    }

}

}
