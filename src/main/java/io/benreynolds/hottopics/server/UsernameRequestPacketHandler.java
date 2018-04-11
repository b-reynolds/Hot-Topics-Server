package io.benreynolds.hottopics.server;

import io.benreynolds.hottopics.packets.Packet;
import io.benreynolds.hottopics.packets.SendMessagePacket;
import io.benreynolds.hottopics.packets.UsernameRequestPacket;
import io.benreynolds.hottopics.packets.UsernameResponsePacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class UsernameRequestPacketHandler implements PacketHandler<UsernameRequestPacket> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsernameRequestPacketHandler.class);

    @Override
    public void handlePacket(Packet packet, Client sender, Map<Session, Client> clients, List<Chatroom> chatrooms) {
        LOGGER.info(String.format("[%s] Processing [%s]...", sender.getSession().getId(), UsernameRequestPacket.class.getSimpleName()));

        UsernameRequestPacket usernameRequestPacket = (UsernameRequestPacket)packet;

        if(usernameRequestPacket == null || !usernameRequestPacket.isValid()) {
            LOGGER.info(String.format("[%s] Invalid [%s] received.", sender.getSession().getId(), UsernameRequestPacket.class.getSimpleName()));
            sender.sendPacket(new UsernameResponsePacket(false));
            return;
        }

        LOGGER.info(String.format("[%s] Checking if username \"%s\" is unique...", sender.getSession().getId(), usernameRequestPacket.getUsername()));
        for(Client client : clients.values()) {
            if(Objects.equals(client.getSession().getId(), sender.getSession().getId()) || client.getUsername() == null) {
                continue;
            }

            if(usernameRequestPacket.getUsername().toUpperCase().equals(client.getUsername().toUpperCase())) {
                LOGGER.info(String.format("[%s] Requested username has already been taken by client [%s].", sender.getSession().getId(), client.getSession().getId()));
                sender.sendPacket(new UsernameResponsePacket(false));
                return;
            }
        }

        sender.setUsername(usernameRequestPacket.getUsername());
        sender.setState(Client.State.ROOM_LIST);

        LOGGER.info(String.format("[%s] Username successfully assigned.", sender.getSession().getId()));

        sender.sendPacket(new UsernameResponsePacket(true));
    }

    @Override
    public Class<UsernameRequestPacket> getType() {
        return UsernameRequestPacket.class;
    }

    @Override
    public List<Client.State> getRequiredStates() {
        return Collections.singletonList(Client.State.NO_USERNAME);
    }

}
