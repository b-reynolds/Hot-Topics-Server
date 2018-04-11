package io.benreynolds.hottopics.server;

import io.benreynolds.hottopics.packets.Packet;

import javax.websocket.Session;
import java.io.IOException;

public class Client {

    public State getState() {
        return mState;
    }

    public void setState(State mState) {
        this.mState = mState;
    }

    public enum State { NO_USERNAME, ROOM_LIST, CHAT_ROOM }

    private Session mSession;
    private String mUsername;
    private State mState;

    Client(final Session session) {
        mSession = session;
    }

    public Session getSession() {
        return mSession;
    }

    public void sendPacket(final Packet packet) {
        try {
            mSession.getBasicRemote().sendText(packet.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setSession(Session mSession) {
        this.mSession = mSession;
    }

    public String getUsername() {
        return mUsername;
    }

    public void setUsername(String mUsername) {
        this.mUsername = mUsername;
    }
}
