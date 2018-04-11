package io.benreynolds.hottopics.server;

import io.benreynolds.hottopics.packets.ChatroomsRequestPacket;
import io.benreynolds.hottopics.packets.ChatroomsResponsePacket;
import io.benreynolds.hottopics.packets.Packet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@code ChatroomsRequestPacketHandler} is responsible for responding to the '{@code ChatroomsResponsePacket}'s that the server
 * receives and handles the sending of available chatrooms to clients.
 */
public class ChatroomsRequestPacketHandler implements PacketHandler<ChatroomsRequestPacket> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatroomsRequestPacketHandler.class);

    @Override
    public void handlePacket(Packet packet, Client sender, Map<Session, Client> clients, List<Chatroom> chatrooms) {
        LOGGER.info(String.format("[%s] Processing [%s]...", sender.getSession().getId(), ChatroomsRequestPacket.class.getSimpleName()));

        // Ensure that the received Packet is non-null and valid.
        ChatroomsRequestPacket chatroomsRequestPacket = (ChatroomsRequestPacket)packet;
        if(chatroomsRequestPacket == null || !chatroomsRequestPacket.isValid()) {
            LOGGER.info(String.format("[%s] Invalid [%s] received.", sender.getSession().getId(), ChatroomsRequestPacket.class.getSimpleName()));
            return;
        }

        // Send a packet to the sender containing the currently available chatrooms.
        sender.sendPacket(new ChatroomsResponsePacket(chatrooms.toArray(new Chatroom[chatrooms.size()])));
    }

    @Override
    public Class<ChatroomsRequestPacket> getType() {
        return ChatroomsRequestPacket.class;
    }

    @Override
    public List<Client.State> getRequiredStates() {
        return Collections.singletonList(Client.State.ROOM_LIST);
    }

}
