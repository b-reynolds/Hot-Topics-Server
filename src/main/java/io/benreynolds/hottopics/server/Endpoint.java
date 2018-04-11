package io.benreynolds.hottopics.server;

import io.benreynolds.hottopics.packets.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.util.*;

@ServerEndpoint("/chat")
public class Endpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotTopicsEndpoint.class);

    private static final Map<Session, Client> CONNECTED_CLIENTS = Collections.synchronizedMap(new HashMap<>());
    private static final List<Chatroom> CHATROOMS = Collections.synchronizedList(new ArrayList<>());

    private static List<PacketHandler> PACKET_HANDLERS = Collections.synchronizedList(Arrays.asList(
            new SendMessagePacketHandler(),
            new ReceiveMessagePacketHandler(),
            new UsernameRequestPacketHandler()));

    @OnOpen
    public static void onOpen(final Session session) {
        LOGGER.info(String.format("[%s] Opened.", session.getId()));


        Client newClient = new Client(session);

        newClient.setState(Client.State.NO_USERNAME);

        CONNECTED_CLIENTS.put(session, new Client(session));
    }

    @OnMessage
    public static void onMessage(final String message, final Session session) {
        // Attempt to de-serialize the received message into a valid Packet instance.
        LOGGER.info(String.format("[%s] Sent message: \"%s\".", session.getId(), message));
        LOGGER.info(String.format("[%s] Attempting to de-serialize message into a Packet...", session.getId()));
        UnidentifiedPacket unidentifiedPacket = PacketIdentifier.convertToPacket(message, UnidentifiedPacket.class);
        if(unidentifiedPacket == null) {
            LOGGER.warn(String.format("[%s] Failed to convert message into a valid Packet instance.", session.getId()));
            return;
        }

        Class<?> packetType = unidentifiedPacket.getType();
        LOGGER.info(String.format("[%s] Successfully identified message as a valid packet of type \"%s\".", session.getId(), packetType.getSimpleName()));

        // Handle the Packet.
        for(PacketHandler packetHandler : PACKET_HANDLERS) {
            if(packetHandler.getType().equals(packetType)) {
                packetHandler.handlePacket(PacketIdentifier.convertToPacket(message, packetHandler.getType()), CONNECTED_CLIENTS.get(session), CONNECTED_CLIENTS, CHATROOMS);
            }
        }
    }

    @OnClose
    public static void onClose(final Session session) {
        LOGGER.info(String.format("[%s] Closed.", session.getId()));
    }


}
