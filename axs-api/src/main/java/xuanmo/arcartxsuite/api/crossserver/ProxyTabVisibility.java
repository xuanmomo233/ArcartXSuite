package xuanmo.arcartxsuite.api.crossserver;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
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
    public static final byte PLAYER_LIST_DETAILS = 9;
    public static final byte CROSS_SERVER_LIST = 10;
    public static final int MAX_REMOTE_PLAYERS = 80;
    public static final int MAX_PERMISSION_WORDS = 4;
    public static final int MAX_SORT_BYTES = 128;
    // A full 80-player snapshot, including rule inputs and these names, fits the plugin-message limit.
    public static final int MAX_DISPLAY_NAME_BYTES = 112;
    public static final int STATE_MESSAGE_LENGTH = Byte.BYTES + Long.BYTES;
    private static final int MAX_NAME_BYTES = 64;
    private static final int INPUT_KEY_BYTES = 32;
    private static final AtomicLong SEQUENCE = new AtomicLong();

    public record PlayerDetails(String inputsKey, List<Long> permissionMask, String sortValue, String displayName) {
        public PlayerDetails {
            permissionMask = List.copyOf(permissionMask);
        }

        public PlayerDetails(String inputsKey, List<Long> permissionMask, String sortValue) {
            this(inputsKey, permissionMask, sortValue, null);
        }
    }

    public record RemotePlayer(UUID uuid, String name, int latency, PlayerDetails details) {}

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

    public static byte[] playerDetailsMessage(PlayerDetails details, long sequence) {
        try (var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes)) {
            output.writeByte(PLAYER_LIST_DETAILS);
            output.writeLong(sequence);
            writeDetails(output, details);
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public static PlayerDetails readPlayerDetails(byte[] message) {
        try {
            var input = payload(message, PLAYER_LIST_DETAILS);
            var details = readDetails(input);
            requireEnd(input);
            return details;
        } catch (BufferUnderflowException failure) {
            throw new IllegalArgumentException("Truncated Tab player details", failure);
        }
    }

    public static byte[] crossServerListMessage(List<RemotePlayer> players, long sequence) {
        if (players.size() > MAX_REMOTE_PLAYERS) throw new IllegalArgumentException("Too many remote Tab players");
        try (var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes)) {
            output.writeByte(CROSS_SERVER_LIST);
            output.writeLong(sequence);
            output.writeByte(players.size());
            for (var player : players) {
                output.writeLong(player.uuid().getMostSignificantBits());
                output.writeLong(player.uuid().getLeastSignificantBits());
                writeText(output, player.name(), MAX_NAME_BYTES);
                output.writeInt(player.latency());
                writeDetails(output, player.details());
            }
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public static List<RemotePlayer> readCrossServerList(byte[] message) {
        try {
            var input = payload(message, CROSS_SERVER_LIST);
            int count = Byte.toUnsignedInt(input.get());
            if (count > MAX_REMOTE_PLAYERS) throw new IllegalArgumentException("Too many remote Tab players");
            var ids = new HashSet<UUID>();
            var players = new ArrayList<RemotePlayer>(count);
            for (int i = 0; i < count; i++) {
                var id = new UUID(input.getLong(), input.getLong());
                if (!ids.add(id)) throw new IllegalArgumentException("Duplicate remote Tab player");
                String name = readText(input, MAX_NAME_BYTES);
                int latency = input.getInt();
                if (name.isBlank() || latency < 0) throw new IllegalArgumentException("Invalid remote Tab player");
                players.add(new RemotePlayer(id, name, latency, readDetails(input)));
            }
            requireEnd(input);
            return List.copyOf(players);
        } catch (BufferUnderflowException failure) {
            throw new IllegalArgumentException("Truncated cross-server Tab list", failure);
        }
    }

    private static ByteBuffer payload(byte[] message, byte operation) {
        if (message.length <= STATE_MESSAGE_LENGTH || message[0] != operation || sequence(message) <= 0) {
            throw new IllegalArgumentException("Invalid Tab player list message");
        }
        return ByteBuffer.wrap(message).position(STATE_MESSAGE_LENGTH);
    }

    private static void requireEnd(ByteBuffer input) {
        if (input.hasRemaining()) throw new IllegalArgumentException("Unexpected Tab player list data");
    }

    private static void writeDetails(DataOutputStream output, PlayerDetails details) throws IOException {
        output.writeByte(details == null ? 0 : 1);
        if (details == null) return;
        byte[] key = HexFormat.of().parseHex(details.inputsKey());
        if (key.length != INPUT_KEY_BYTES || details.permissionMask().size() > MAX_PERMISSION_WORDS) {
            throw new IllegalArgumentException("Invalid Tab rule inputs");
        }
        output.write(key);
        output.writeByte(details.permissionMask().size());
        for (long word : details.permissionMask()) output.writeLong(word);
        writeText(output, details.sortValue() == null ? "" : details.sortValue(), MAX_SORT_BYTES);
        writeText(output, details.displayName() == null ? "" : details.displayName(), MAX_DISPLAY_NAME_BYTES);
    }

    private static PlayerDetails readDetails(ByteBuffer input) {
        byte present = input.get();
        if (present == 0) return null;
        if (present != 1) throw new IllegalArgumentException("Invalid Tab player details flag");
        byte[] key = new byte[INPUT_KEY_BYTES];
        input.get(key);
        int words = Byte.toUnsignedInt(input.get());
        if (words > MAX_PERMISSION_WORDS) throw new IllegalArgumentException("Too many Tab permission inputs");
        var permissionMask = new ArrayList<Long>(words);
        for (int i = 0; i < words; i++) permissionMask.add(input.getLong());
        String sortValue = readText(input, MAX_SORT_BYTES);
        String displayName = readText(input, MAX_DISPLAY_NAME_BYTES);
        return new PlayerDetails(HexFormat.of().formatHex(key), permissionMask, sortValue.isEmpty() ? null : sortValue,
            displayName.isEmpty() ? null : displayName);
    }

    private static void writeText(DataOutputStream output, String value, int maximum) throws IOException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        if (data.length > maximum) throw new IllegalArgumentException("Tab value exceeds maximum length");
        output.writeByte(data.length);
        output.write(data);
    }

    private static String readText(ByteBuffer input, int maximum) {
        int length = Byte.toUnsignedInt(input.get());
        if (length > maximum) throw new IllegalArgumentException("Tab value exceeds maximum length");
        byte[] data = new byte[length];
        input.get(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    private ProxyTabVisibility() {}
}
