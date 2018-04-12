package io.benreynolds.hottopics.server;

import io.benreynolds.hottopics.packets.Packet;

import javax.websocket.Session;
import java.io.IOException;
import java.time.LocalTime;

public class Client {

    public State getState() {
        return mState;
    }

    public void setState(State mState) {
        this.mState = mState;
    }

    public Chatroom getChatroom() {
        return mChatroom;
    }

    public void setChatroom(Chatroom mChatroom) {
        this.mChatroom = mChatroom;
    }

    public boolean requiresAcknowledgementRequest() {
        return mRequiresAcknowledgementRequest;
    }

    public void setRequiresAcknowledgementRequest(boolean mRequiresAcknowledgementRequest) {
        this.mRequiresAcknowledgementRequest = mRequiresAcknowledgementRequest;
    }

    public LocalTime getTimeAcknowledgementRequestWasSent() {
        return mTimeAcknowledgementRequestWasSent;
    }

    public void setTimeAcknowledgementRequestWasSent(LocalTime mTimeSinceAcknowledgementRequest) {
        this.mTimeAcknowledgementRequestWasSent = mTimeSinceAcknowledgementRequest;
    }

    public LocalTime getLastMessageTime() {
        return mLastMessageTime;
    }

    public void setLastMessageTime(LocalTime mLastMessageTime) {
        this.mLastMessageTime = mLastMessageTime;
    }

    public enum State { NO_USERNAME, ROOM_LIST, CHAT_ROOM }

    private Session mSession = null;
    private String mUsername = null;
    private State mState = State.NO_USERNAME;
    private Chatroom mChatroom = null;
    private LocalTime mLastMessageTime = LocalTime.now();
    private LocalTime mTimeAcknowledgementRequestWasSent = LocalTime.now();
    private boolean mRequiresAcknowledgementRequest = false;

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
