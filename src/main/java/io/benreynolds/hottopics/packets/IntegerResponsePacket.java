package io.benreynolds.hottopics.packets;

import com.google.gson.annotations.SerializedName;

/**
 * {@code IntegerResponsePacket} is a generic integer response packet implementation used by packets such as
 * {@code JoinChatroomResponsePacket} and {@code UsernameResponsePacket}.
 */
public abstract class IntegerResponsePacket extends Packet {

    /** Server's response. */
    @SerializedName("response")
    private int mResponse;

    /**
     * @param response Server's response.
     */
    IntegerResponsePacket(final int response) {
        mResponse = response;
    }

    /**
     * Returns the server's response.
     * @return Server's response.
     */
    public int getResponse() {
        return mResponse;
    }

    /**
     * Returns {@code true} if the {@code IntegerResponsePacket} contains a valid ID.
     * @return {@code true} if the {@code IntegerResponsePacket} contains a valid ID.
     */
    @Override
    public boolean isValid() {
        return mId != null;
    }

}
