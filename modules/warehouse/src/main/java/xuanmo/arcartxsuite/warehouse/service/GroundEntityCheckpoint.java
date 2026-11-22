package xuanmo.arcartxsuite.warehouse.service;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Reads the current on-disk entity chunk to confirm a positive receipt marker.
 * All calls belong on a worker. A missing or mismatched entity is not proof of removal.
 */
final class GroundEntityCheckpoint {
    private static final int SECTOR_BYTES = 4096;
    private static final int MAX_NBT_BYTES = 8 * 1024 * 1024;
    private static final int MAX_COLLECTION_ENTRIES = 200_000;
    private static final int MAX_DEPTH = 64;
    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;

    enum Result { CONFIRMED, UNCONFIRMED, UNKNOWN }

    record Expected(UUID entityId, String operationId, int amount) {
    }

    private GroundEntityCheckpoint() {
    }

    static Result read(Path worldDirectory, int chunkX, int chunkZ, UUID entityId, String markerKey,
                       String operationId, int expectedAmount) {
        return readBatch(worldDirectory, chunkX, chunkZ, markerKey, List.of(new Expected(entityId, operationId, expectedAmount)))
            .getOrDefault(entityId, Result.UNKNOWN);
    }

    static Map<UUID, Result> readBatch(Path worldDirectory, int chunkX, int chunkZ, String markerKey,
                                       List<Expected> expected) {
        Map<UUID, Result> receipts = new HashMap<>();
        Map<UUID, Expected> targets = new HashMap<>();
        for (Expected entry : expected) {
            UUID entityId = entry.entityId();
            if (receipts.put(entityId, Result.UNKNOWN) != null) {
                targets.remove(entityId);
            } else if (entityId != null && entry.amount() > 0
                && entry.operationId() != null && !entry.operationId().isEmpty()) {
                targets.put(entityId, entry);
            }
        }
        if (targets.isEmpty() || markerKey == null || markerKey.isEmpty()) return receipts;
        Path region = worldDirectory.resolve("entities").resolve(
            "r." + Math.floorDiv(chunkX, 32) + "." + Math.floorDiv(chunkZ, 32) + ".mca");
        int index = (chunkX & 31) + (chunkZ & 31) * 32;
        try {
            BasicFileAttributes original = attributes(region);
            RegionChunk first = readChunk(region, index);
            RegionChunk second = readChunk(region, index);
            if (first == null || second == null || !first.matches(second)
                || !sameFile(original, attributes(region))) return receipts;
            Map<UUID, Result> matches = inspect(first, chunkX, chunkZ, markerKey, targets);
            targets.keySet().forEach(id -> receipts.put(id, matches.getOrDefault(id, Result.UNCONFIRMED)));
        } catch (IOException | SecurityException failure) {
            // A partial or invalid chunk cannot confirm any requested receipt.
        }
        return receipts;
    }

    private static BasicFileAttributes attributes(Path file) throws IOException {
        var attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) throw new IOException("Expected a regular region file");
        return attributes;
    }

    private static boolean sameFile(BasicFileAttributes first, BasicFileAttributes second) {
        return first.size() == second.size() && first.lastModifiedTime().equals(second.lastModifiedTime())
            && Objects.equals(first.fileKey(), second.fileKey());
    }

    private static RegionChunk readChunk(Path file, int index) throws IOException {
        try (var channel = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            int location = readBytes(channel, 4L * index, Integer.BYTES).getInt();
            int timestamp = readBytes(channel, SECTOR_BYTES + 4L * index, Integer.BYTES).getInt();
            if (location == 0) return null;
            long position = (long) (location >>> 8) * SECTOR_BYTES;
            long capacity = (long) (location & 255) * SECTOR_BYTES;
            long fileSize = channel.size();
            // An open Paper region may not yet have padding after its last chunk.
            if (position < 2L * SECTOR_BYTES || capacity == 0 || position + Integer.BYTES + 1 > fileSize) {
                throw new IOException("Invalid entity region sector range");
            }
            ByteBuffer header = readBytes(channel, position, Integer.BYTES + 1);
            int length = header.getInt();
            int codec = Byte.toUnsignedInt(header.get());
            // Reject external .mcc pointers and unknown codecs, including future format additions.
            if (codec < 1 || codec > 3 || length < 2 || Integer.BYTES + (long) length > capacity
                || position + Integer.BYTES + (long) length > fileSize) {
                throw new IOException("Unsupported or invalid entity region chunk");
            }
            byte[] contents = readBytes(channel, position + Integer.BYTES + 1, length - 1).array();
            if (location != readBytes(channel, 4L * index, Integer.BYTES).getInt()
                || timestamp != readBytes(channel, SECTOR_BYTES + 4L * index, Integer.BYTES).getInt()) {
                throw new IOException("Entity region changed during read");
            }
            return new RegionChunk(location, timestamp, codec, contents);
        }
    }

    private static ByteBuffer readBytes(FileChannel channel, long offset, int length) throws IOException {
        ByteBuffer bytes = ByteBuffer.allocate(length);
        while (bytes.hasRemaining()) {
            if (channel.read(bytes, offset + bytes.position()) <= 0) throw new EOFException();
        }
        return bytes.flip();
    }

    private static Map<UUID, Result> inspect(RegionChunk chunk, int x, int z, String markerKey,
                                             Map<UUID, Expected> targets) throws IOException {
        try (InputStream stream = chunk.open()) {
            byte[] bytes = stream.readNBytes(MAX_NBT_BYTES + 1);
            if (bytes.length > MAX_NBT_BYTES) throw new IOException("Entity chunk exceeds the NBT size limit");
            try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (input.readUnsignedByte() != TAG_COMPOUND) throw new IOException("Invalid entity chunk root");
                input.readUTF();
                boolean positioned = false;
                boolean dataVersion = false;
                Map<UUID, Result> matches = null;
                int type;
                while ((type = input.readUnsignedByte()) != TAG_END) {
                    String field = input.readUTF();
                    if ("Position".equals(field)) {
                        if (type != TAG_INT_ARRAY || positioned || input.readInt() != 2
                            || input.readInt() != x || input.readInt() != z) {
                            throw new IOException("Invalid entity chunk position");
                        }
                        positioned = true;
                    } else if ("DataVersion".equals(field)) {
                        if (type != TAG_INT || dataVersion || input.readInt() <= 0) {
                            throw new IOException("Invalid entity chunk data version");
                        }
                        dataVersion = true;
                    } else if ("Entities".equals(field)) {
                        if (type != TAG_LIST || matches != null) throw new IOException("Invalid entity list tag");
                        matches = inspectEntities(input, markerKey, targets);
                    } else {
                        skip(input, type, 1);
                    }
                }
                if (!positioned || !dataVersion || matches == null || input.read() != -1) {
                    throw new IOException("Incomplete or invalid entity chunk");
                }
                return matches;
            }
        }
    }

    private static Map<UUID, Result> inspectEntities(DataInputStream input, String markerKey,
                                                      Map<UUID, Expected> targets) throws IOException {
        int type = input.readUnsignedByte();
        int count = readLength(input);
        if (type == TAG_END && count == 0) return Map.of();
        if (type != TAG_COMPOUND) throw new IOException("Invalid entity list");
        Map<UUID, Result> matches = new HashMap<>();
        for (int i = 0; i < count; i++) {
            CheckpointEntity entity = readEntity(input, markerKey);
            Expected expected = targets.get(entity.id());
            if (expected == null) continue;
            Result result = entity.item() && entity.amount() == expected.amount() && expected.operationId().equals(entity.marker())
                ? Result.CONFIRMED : Result.UNCONFIRMED;
            matches.merge(entity.id(), result, (previous, duplicate) -> Result.UNKNOWN);
        }
        return matches;
    }

    private static CheckpointEntity readEntity(DataInputStream input, String markerKey) throws IOException {
        UUID id = null;
        Long most = null;
        Long least = null;
        String entityType = null;
        boolean itemData = false;
        boolean bukkitValues = false;
        String marker = null;
        int amount = -1;
        int type;
        while ((type = input.readUnsignedByte()) != TAG_END) {
            String field = input.readUTF();
            if ("UUID".equals(field)) {
                if (type != TAG_INT_ARRAY || id != null || input.readInt() != 4) {
                    throw new IOException("Invalid entity UUID");
                }
                id = new UUID(((long) input.readInt() << 32) | Integer.toUnsignedLong(input.readInt()),
                    ((long) input.readInt() << 32) | Integer.toUnsignedLong(input.readInt()));
            } else if ("UUIDMost".equals(field)) {
                if (type != TAG_LONG || most != null) throw new IOException("Invalid UUIDMost");
                most = input.readLong();
            } else if ("UUIDLeast".equals(field)) {
                if (type != TAG_LONG || least != null) throw new IOException("Invalid UUIDLeast");
                least = input.readLong();
            } else if ("id".equals(field)) {
                if (type != TAG_STRING || entityType != null) throw new IOException("Invalid entity id");
                entityType = input.readUTF();
            } else if ("Item".equals(field)) {
                if (type != TAG_COMPOUND || itemData) throw new IOException("Invalid item payload");
                amount = readAmount(input);
                itemData = true;
            } else if ("BukkitValues".equals(field)) {
                if (type != TAG_COMPOUND || bukkitValues) throw new IOException("Invalid Bukkit values");
                marker = readMarker(input, markerKey);
                bukkitValues = true;
            } else {
                skip(input, type, 3);
            }
        }
        if (most != null || least != null) {
            if (id != null || most == null || least == null) throw new IOException("Conflicting entity UUID formats");
            id = new UUID(most, least);
        }
        return new CheckpointEntity(id, "minecraft:item".equals(entityType), amount, marker);
    }

    private static String readMarker(DataInputStream input, String markerKey) throws IOException {
        String marker = null;
        int type;
        while ((type = input.readUnsignedByte()) != TAG_END) {
            if (markerKey.equals(input.readUTF())) {
                if (type != TAG_STRING || marker != null) throw new IOException("Invalid receipt marker");
                marker = input.readUTF();
            } else {
                skip(input, type, 4);
            }
        }
        return marker;
    }

    private static int readAmount(DataInputStream input) throws IOException {
        int amount = -1, type;
        while ((type = input.readUnsignedByte()) != TAG_END) {
            String field = input.readUTF();
            if ("Count".equals(field) || "count".equals(field)) {
                if (amount != -1 || ("Count".equals(field) ? type != TAG_BYTE : type != TAG_INT)) {
                    throw new IOException("Invalid item count");
                }
                amount = type == TAG_BYTE ? input.readByte() : input.readInt();
                if (amount <= 0) throw new IOException("Invalid item count");
            } else {
                skip(input, type, 4);
            }
        }
        return amount;
    }

    private static int readLength(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_COLLECTION_ENTRIES) throw new IOException("Invalid NBT collection length");
        return count;
    }

    private static void skip(DataInputStream input, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT nesting limit");
        switch (type) {
            case 1 -> input.skipNBytes(1);
            case 2 -> input.skipNBytes(2);
            case 3, 5 -> input.skipNBytes(4);
            case 4, 6 -> input.skipNBytes(8);
            case 7 -> input.skipNBytes(readLength(input));
            case 8 -> input.skipNBytes(input.readUnsignedShort());
            case 9 -> {
                int element = input.readUnsignedByte();
                int count = readLength(input);
                for (int i = 0; i < count; i++) skip(input, element, depth + 1);
            }
            case 10 -> {
                int nested;
                while ((nested = input.readUnsignedByte()) != TAG_END) {
                    input.skipNBytes(input.readUnsignedShort());
                    skip(input, nested, depth + 1);
                }
            }
            case 11 -> input.skipNBytes(4L * readLength(input));
            case 12 -> input.skipNBytes(8L * readLength(input));
            default -> throw new IOException("Invalid NBT type");
        }
    }

    private record CheckpointEntity(UUID id, boolean item, int amount, String marker) {
    }

    private record RegionChunk(int location, int timestamp, int codec, byte[] contents) {
        boolean matches(RegionChunk other) {
            return location == other.location && timestamp == other.timestamp && codec == other.codec
                && Arrays.equals(contents, other.contents);
        }

        InputStream open() throws IOException {
            InputStream source = new ByteArrayInputStream(contents);
            return switch (codec) {
                case 1 -> new GZIPInputStream(source);
                case 2 -> new InflaterInputStream(source);
                case 3 -> source;
                default -> throw new IOException("Invalid compression codec");
            };
        }
    }
}
