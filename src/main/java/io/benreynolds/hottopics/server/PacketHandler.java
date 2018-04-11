package io.benreynolds.hottopics.server;

import io.benreynolds.hottopics.packets.Packet;

import javax.websocket.Session;
import java.util.List;
import java.util.Map;

public interface PacketHandler<T extends Packet> {

    void handlePacket(final Packet packet, final Client sender, final Map<Session, Client> clients, final List<Chatroom> chatrooms);
    Class<T> getType();
    List<Client.State> getRequiredStates();

}

