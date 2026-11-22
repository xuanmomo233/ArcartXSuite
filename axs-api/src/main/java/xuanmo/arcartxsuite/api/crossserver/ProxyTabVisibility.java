package xuanmo.arcartxsuite.api.crossserver;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/** Messages scoped to a player's current proxy-to-backend connection. */
public final class ProxyTabVisibility {
    public static final String CHANNEL = "axs:tab_visibility";
    // Carried by Velocity modern forwarding and consumed by Essentials before native list publication.
    public static final String VANISH_PROFILE_PROPERTY = "axs_essentials_vanished";
    public static final byte QUERY = 0;
    public static final byte VISIBLE = 1;
    public static final byte VANISHED = 2;
    public static final byte RESTRICTED = 3;
    public static final byte WITHDRAWN = 4;
    public static final byte VANISHED_RESTRICTED = 5;
    public static final byte VANISH_REQUEST = 6;
    public static final byte VANISH_GRANTED = 7;
    public static final byte VANISH_DENIED = 8;
    public static final int STATE_MESSAGE_LENGTH = Byte.BYTES + Long.BYTES;
    private static final AtomicLong SEQUENCE = new AtomicLong();

    /** Shared by backend publishers so a Tab reload cannot reset the current connection's sequence. */
    public static long nextSequence() {
        return SEQUENCE.incrementAndGet();
    }

    public static byte[] stateMessage(byte operation, long sequence) {
        return ByteBuffer.allocate(STATE_MESSAGE_LENGTH).put(operation).putLong(sequence).array();
    }

    public static long sequence(byte[] message) {
        return ByteBuffer.wrap(message).getLong(Byte.BYTES);
    }

    private ProxyTabVisibility() {}
}
