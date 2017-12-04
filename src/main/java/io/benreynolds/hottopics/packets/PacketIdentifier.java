package io.benreynolds.hottopics.packets;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code PacketIdentifier} is a static class that upon initialization, creates a map in which it assigns unique IDs
 * to {@code Packet} derivatives. These IDs are used to identify the types of '{@code Packet}'s before deserialization.
 */
class PacketIdentifier {

    /** Populated with {@code Packet} and {@code Integer} key/value pairs that are used to uniquely identify
     * {@code Packet} derivatives before deserialization. */
    final static Map<Class<? extends Packet>, Integer> PACKET_IDS = new HashMap<>();

    /** Contains all known and supported {@code Packet} derivatives. */
    private static final List<Class<? extends Packet>> PACKETS = Arrays.asList(
        SendMessagePacket.class,
        ReceiveMessagePacket.class,
        UsernameRequestPacket.class,
        UsernameResponsePacket.class
    );

    /** Current ID value. */
    private static int mId;

    static {
        // Assign all known packets a unique ID number and add them to the map of known packets.
        for(Class<? extends Packet> packetDerivative : PACKETS) {
            PACKET_IDS.put(packetDerivative, mId++);
        }
    }

}