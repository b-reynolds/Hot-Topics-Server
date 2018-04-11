package io.benreynolds.hottopics.server;

import io.benreynolds.hottopics.packets.Packet;
import io.benreynolds.hottopics.packets.SendMessagePacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SendMessagePacketHandler implements PacketHandler<SendMessagePacket> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendMessagePacketHandler.class);

    @Override
    public void handlePacket(Packet packet, Client sender, Map<Session, Client> clients, List<Chatroom> chatrooms) {
        SendMessagePacket sendMessagePacket = (SendMessagePacket)packet;
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
