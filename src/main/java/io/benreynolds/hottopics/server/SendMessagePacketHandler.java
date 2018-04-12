package io.benreynolds.hottopics.server;

import io.benreynolds.hottopics.packets.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@code SendMessagePacketHandler} is responsible for responding to the '{@code SendMessagePacket}'s that the server
 * receives and handles and relaying the messages within to clients within the same chatrooms
 */
public class SendMessagePacketHandler implements PacketHandler<SendMessagePacket> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendMessagePacketHandler.class);

    @Override
    public void handlePacket(Packet packet, Client sender, Map<Session, Client> clients, List<Chatroom> chatrooms) {
        LOGGER.info(String.format("[%s] Processing [%s]...", sender.getSession().getId(), SendMessagePacket.class.getSimpleName()));

        // Ensure that the received Packet is non-null and valid.
        SendMessagePacket sendMessagePacket = (SendMessagePacket)packet;
        if(sendMessagePacket == null || !sendMessagePacket.isValid()) {
            LOGGER.info(String.format("[%s] Invalid \"%s\" provided, failed to handle.", sender.getSession().getId(), SendMessagePacket.class.getSimpleName()));
            return;
        }

        if(sender.getChatroom() == null) {
            return;
        }

        LOGGER.info(String.format("[%s] Sending message to all clients in room [%s]...", sender.getSession().getId(), sender.getChatroom().getName()));
        ReceiveMessagePacket receiveMessagePacket = new ReceiveMessagePacket(sender.getUsername(), sendMessagePacket.getMessage());

        Chatroom chatroom = sender.getChatroom();
        chatroom.addMessage(receiveMessagePacket);
        for(Client client : chatroom.getClients()) {
            client.sendPacket(receiveMessagePacket);
        }
    }

    @Override
    public Class<SendMessagePacket> getType() {
        return SendMessagePacket.class;
    }

    @Override
    public List<Client.State> getRequiredStates() {
        return Collections.singletonList(Client.State.CHAT_ROOM);
    }

}
