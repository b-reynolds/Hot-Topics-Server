package io.benreynolds.hottopics.packets;

import com.google.gson.annotations.SerializedName;

public abstract class BooleanResponsePacket extends Packet {

    /** Server's response. */
    @SerializedName("response")
    private boolean mResponse;

    /**
     * @param response Server's response.
     */
    public BooleanResponsePacket(final boolean response) {
        mResponse = response;
    }

    /**
     * Returns the server's response.
     * @return Server's response.
     */
    public boolean getResponse() {
        return mResponse;
    }

    /**
     * Returns {@code true} if the {@code BooleanResponsePacket} contains a valid ID.
     * @return {@code true} if the {@code BooleanResponsePacket} contains a valid ID.
     */
    @Override
    public boolean isValid() {
        return mId != null;
    }

}