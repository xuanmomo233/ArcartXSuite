package xuanmo.arcartxsuite.warehouse.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroundEntityCheckpointTest {
    private static final UUID SOURCE = UUID.fromString("085e13e7-b99d-44fc-946f-d048aeec0c38");
    private static final UUID SECOND = UUID.fromString("fa77a60e-735a-45d5-8c84-918670e86e7a");
    private static final UUID ABSENT = UUID.fromString("ae2822a0-16f7-4f3f-902c-8edfc5ebdf61");
    private static final String KEY = "arcartxsuite:warehouse_ground_transfer";
    private static final String OPERATION = "1f6cee6c-bfa2-404e-8b15-b3f81cc4fedf:before";
    @TempDir Path temp;

    @Test
    void acceptsUnpaddedLiveChunkButRejectsATruncatedPayload() throws Exception {
        Path file = writeRegion(new GroundEntityCheckpoint.Expected(SOURCE, OPERATION, 6));
        assertEquals(GroundEntityCheckpoint.Result.CONFIRMED, read());

        byte[] region = Files.readAllBytes(file);
        Files.write(file, Arrays.copyOf(region, region.length - 1));
        assertEquals(GroundEntityCheckpoint.Result.UNKNOWN, read());
    }

    @Test
    void confirmsDistinctEntriesAndRejectsDuplicateAndMismatchedReceipts() throws Exception {
        var source = new GroundEntityCheckpoint.Expected(SOURCE, OPERATION, 6);
        var second = new GroundEntityCheckpoint.Expected(SECOND, OPERATION + "-second", 9);
        var absent = new GroundEntityCheckpoint.Expected(ABSENT, OPERATION, 6);
        writeRegion(source, second);

        var results = GroundEntityCheckpoint.readBatch(temp, 0, 0, KEY, List.of(source, second, absent));
        assertEquals(GroundEntityCheckpoint.Result.CONFIRMED, results.get(SOURCE));
        assertEquals(GroundEntityCheckpoint.Result.CONFIRMED, results.get(SECOND));
        assertEquals(GroundEntityCheckpoint.Result.UNCONFIRMED, results.get(ABSENT));

        var wrongAmount = new GroundEntityCheckpoint.Expected(SECOND, second.operationId(), 10);
        var wrongMarker = new GroundEntityCheckpoint.Expected(SOURCE, OPERATION + "-other", 6);
        results = GroundEntityCheckpoint.readBatch(temp, 0, 0, KEY, List.of(wrongAmount, wrongMarker));
        assertEquals(GroundEntityCheckpoint.Result.UNCONFIRMED, results.get(SOURCE));
        assertEquals(GroundEntityCheckpoint.Result.UNCONFIRMED, results.get(SECOND));

        results = GroundEntityCheckpoint.readBatch(temp, 0, 0, KEY, List.of(source, second, source));
        assertEquals(GroundEntityCheckpoint.Result.UNKNOWN, results.get(SOURCE));
        assertEquals(GroundEntityCheckpoint.Result.CONFIRMED, results.get(SECOND));

        writeRegion(source, second, source);
        results = GroundEntityCheckpoint.readBatch(temp, 0, 0, KEY, List.of(source, second));
        assertEquals(GroundEntityCheckpoint.Result.UNKNOWN, results.get(SOURCE));
        assertEquals(GroundEntityCheckpoint.Result.CONFIRMED, results.get(SECOND));
    }

    private GroundEntityCheckpoint.Result read() {
        return GroundEntityCheckpoint.read(temp, 0, 0, SOURCE, KEY, OPERATION, 6);
    }

    private Path writeRegion(GroundEntityCheckpoint.Expected... entities) throws Exception {
        byte[] chunk = compressedEntities(entities);
        var region = ByteBuffer.allocate(8192 + 5 + chunk.length);
        region.putInt((2 << 8) | 1);
        region.position(8192);
        region.putInt(chunk.length + 1).put((byte) 2).put(chunk);
        var directory = Files.createDirectories(temp.resolve("entities"));
        return Files.write(directory.resolve("r.0.0.mca"), region.array());
    }

    private static byte[] compressedEntities(GroundEntityCheckpoint.Expected... entities) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            tag(out, 10, "");
            tag(out, 3, "DataVersion");
            out.writeInt(3465);
            tag(out, 11, "Position");
            out.writeInt(2);
            out.writeInt(0);
            out.writeInt(0);
            tag(out, 9, "Entities");
            out.writeByte(10);
            out.writeInt(entities.length);
            for (var entry : entities) {
                tag(out, 8, "id");
                out.writeUTF("minecraft:item");
                tag(out, 11, "UUID");
                out.writeInt(4);
                out.writeLong(entry.entityId().getMostSignificantBits());
                out.writeLong(entry.entityId().getLeastSignificantBits());
                tag(out, 10, "Item");
                tag(out, 1, "Count");
                out.writeByte(entry.amount());
                out.writeByte(0);
                tag(out, 10, "BukkitValues");
                tag(out, 8, KEY);
                out.writeUTF(entry.operationId());
                out.writeByte(0);
                out.writeByte(0);
            }
            out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static void tag(DataOutputStream out, int type, String name) throws Exception {
        out.writeByte(type);
        out.writeUTF(name);
    }
}
