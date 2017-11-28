package io.benreynolds.hottopics.server;

import java.util.Date;

public class Message {

    private String mContent;
    private String mSender;
    private Date mReceived;

    public String getContent() {
        return mContent;
    }

    public void setContent(final String mContent) {
        this.mContent = mContent;
    }

    public String getSender() {
        return mSender;
    }

    public void setSender(final String mSender) {
        this.mSender = mSender;
    }

    public Date getReceived() {
        return mReceived;
    }

    public void setReceived(Date mReceived) {
        this.mReceived = mReceived;
    }

}
