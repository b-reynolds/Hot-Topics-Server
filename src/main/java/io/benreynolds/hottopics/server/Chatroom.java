    package io.benreynolds.hottopics.server;

    import com.google.gson.annotations.SerializedName;
    import io.benreynolds.hottopics.packets.ReceiveMessagePacket;

    import javax.websocket.Session;

    import java.util.Collections;
    import java.util.HashSet;
    import java.util.LinkedList;
    import java.util.Queue;
    import java.util.Set;

    /**
     * '{@code Chatroom}'s are named using trend information (see {@code TrendManager}) and contain references to the user's
     * '{@code Session}'s that are present within them.
     */
    public class Chatroom {

        private static final int MESSAGES_TO_CACHE = 50;

        /** {@code Set} used to store all users ('{@code Session}'s) that are members of the chatroom. */
        private transient Set<Session> mMembers = Collections.synchronizedSet(new HashSet<Session>());

        /** Amount of users in the Chatroom **/
        @SerializedName("size")
        private Integer mSize = 0;

        /** Name of the {@code Chatroom}. */
        @SerializedName("name")
        private String mName;

        /** Messages that have been sent within the {@code Chatroom}. */
        @SerializedName("messages")
        private Queue<ReceiveMessagePacket> mMessages = new LinkedList<>();

        /**
         * @param name Name of the {@code Chatroom}.
         */
        public Chatroom(final String name) {
            mName = name;
        }

        /** Returns a {@code Set} containing all users ('{@code Session}'s) that are members of the chatroom.
         * @return {@code Set} containing all users ('{@code Session}'s) that are members of the chatroom.
         */
        public Set<Session> getMembers() {
            return mMembers;
        }

        /**
         * Adds a {@code Session} to the {@code Chatroom}.
         * @param session {@code Session} to add to the {@code Chatroom}.
         */
        public void addMember(final Session session) {
            if(mMembers.add(session)) {
                mSize++;
            }
        }

        /**
         * Removes a {@code Session} from the {@code Chatroom}.
         * @param session {@code Session} to remove from the {@code Chatroom}.
         */
        public void removeMember(final Session session) {
            if(mMembers.remove(session)) {
                mSize--;
            }
        }

        /**
         * @param session {@code Session} to search for in the {@code Chatroom}.
         * @return {@code true} if the {@code Chatroom} contained the {@code Session}.
         */
        public boolean containsMember(final Session session) {
            return mMembers.contains(session);
        }

        /** Returns the name of the {@code Chatroom}.
         * @return Name of the {@code Chatroom}.
         */
        public String getName() {
            return mName;
        }

        public void addMessage(ReceiveMessagePacket receiveMessagePacket) {
            if(mMessages.size() >= MESSAGES_TO_CACHE) {
                mMessages.poll();
            }

            mMessages.add(receiveMessagePacket);
        }

        /** Returns the messages that have been sent within the {@code Chatroom}.
         * @return Messages that have been sent within the {@code Chatroom}.
         */
        public Queue<ReceiveMessagePacket> getMessages() {
            return mMessages;
        }

    }
