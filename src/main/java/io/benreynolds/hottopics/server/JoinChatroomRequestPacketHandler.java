package io.benreynolds.hottopics.server;

import io.benreynolds.hottopics.packets.JoinChatroomRequestPacket;
import io.benreynolds.hottopics.packets.JoinChatroomResponsePacket;
import io.benreynolds.hottopics.packets.Packet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@code JoinChatroomRequestPacketHandler} is responsible for responding to the '{@code JoinChatroomRequestPacket}'s that the server
 * receives and handles the placement of users within chatrooms.
 */
public class JoinChatroomRequestPacketHandler implements PacketHandler<JoinChatroomRequestPacket> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JoinChatroomRequestPacketHandler.class);

    @Override
    public void handlePacket(Packet packet, Client sender, Map<Session, Client> clients, List<Chatroom> chatrooms) {

        LOGGER.info(String.format("[%s] Processing [%s]...", sender.getSession().getId(), JoinChatroomRequestPacket.class.getSimpleName()));
        // Ensure that the received Packet is non-null and valid.
        JoinChatroomRequestPacket joinChatroomRequestPacket = (JoinChatroomRequestPacket)packet;
        if(joinChatroomRequestPacket == null || !joinChatroomRequestPacket.isValid()) {
            LOGGER.info(String.format("[%s] Invalid \"%s\" provided, failed to handle.", sender.getSession().getId(), JoinChatroomRequestPacket.class.getSimpleName()));
            sender.sendPacket(new JoinChatroomResponsePacket(false));
            return;
        }

        // Search for the specified chatroom and add the user to to it.
        for(Chatroom chatroom : chatrooms) {
            if(!chatroom.getName().equals(joinChatroomRequestPacket.getChatroomName())) {
                continue;
            }

            chatroom.addClient(sender);

            sender.setState(Client.State.CHAT_ROOM);
            sender.setChatroom(chatroom);

            sender.sendPacket(new JoinChatroomResponsePacket(true));

            LOGGER.info(String.format("[%s] Entered chatroom [%s]...", sender.getSession().getId(), chatroom.getName()));

            // Update clients with the new user count information for the specified chatroom.
            HotTopicsEndpoint.sendUpdatedChatroomsListToClients();
            HotTopicsEndpoint.sendUpdatedRoomUserCountToClientsInRoom(chatroom);

            return;
        }

        // The specified room does not exist.
        LOGGER.info(String.format("[%s] Specified chatroom does not exist.", sender.getSession().getId()));
        sender.sendPacket(new JoinChatroomResponsePacket(false));
    }

    @Override
    public Class<JoinChatroomRequestPacket> getType() {
        return JoinChatroomRequestPacket.class;
    }

    @Override
    public List<Client.State> getRequiredStates() {
        return Collections.singletonList(Client.State.ROOM_LIST);
    }

}
