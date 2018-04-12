package io.benreynolds.hottopics.server;

import io.benreynolds.hottopics.packets.LeaveChatroomRequestPacket;
import io.benreynolds.hottopics.packets.LeaveChatroomResponsePacket;
import io.benreynolds.hottopics.packets.Packet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@code LeaveChatroomRequestPacketHandler is responsible for responding to the '{@code LeaveChatroomRequestPacket}'s that the server
 * receives and handles the removal of users from chatrooms.
 */
public class LeaveChatroomRequestPacketHandler implements PacketHandler<LeaveChatroomRequestPacket> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LeaveChatroomRequestPacket.class);

    @Override
    public void handlePacket(Packet packet, Client sender, Map<Session, Client> clients, List<Chatroom> chatrooms) {

        LOGGER.info(String.format("[%s] Processing [%s]...", sender.getSession().getId(), LeaveChatroomRequestPacket.class.getSimpleName()));
        // Ensure that the received Packet is non-null and valid.
        LeaveChatroomRequestPacket leaveChatroomRequestPacket = (LeaveChatroomRequestPacket)packet;
        if(leaveChatroomRequestPacket == null || !leaveChatroomRequestPacket.isValid()) {
            LOGGER.info(String.format("[%s] Invalid \"%s\" provided, failed to handle.", sender.getSession().getId(), LeaveChatroomRequestPacket.class.getSimpleName()));
            sender.sendPacket(new LeaveChatroomResponsePacket(false));
            return;
        }

        // Remove the client from the chatroom and notify other clients of the updated user counts.
        Chatroom chatroomToLeave = sender.getChatroom();
        if(chatroomToLeave != null && chatroomToLeave.containsClient(sender)) {
            LOGGER.info(String.format("[%s] Removing client from room [%s]...", sender.getSession().getId(), chatroomToLeave.getName()));

            chatroomToLeave.removeClient(sender);
            sender.setChatroom(null);
            sender.setState(Client.State.ROOM_LIST);
            sender.sendPacket(new LeaveChatroomResponsePacket(true));

            HotTopicsEndpoint.sendUpdatedRoomUserCountToClientsInRoom(chatroomToLeave);

            return;
        }

        sender.sendPacket(new LeaveChatroomResponsePacket(false));
    }

    @Override
    public Class<LeaveChatroomRequestPacket> getType() {
        return LeaveChatroomRequestPacket.class;
    }

    @Override
    public List<Client.State> getRequiredStates() {
        return Collections.singletonList(Client.State.CHAT_ROOM);
    }

}
