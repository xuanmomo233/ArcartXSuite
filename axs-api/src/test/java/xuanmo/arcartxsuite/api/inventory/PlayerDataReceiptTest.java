package xuanmo.arcartxsuite.api.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerDataReceiptTest {
    private static final String KEY = "arcartxsuite:receipt";

    @TempDir Path directory;

    @Test
    void readsTheMarkerOrAbsenceFromACompleteSave() throws IOException {
        Path file = writeSave("applied", true);
        assertEquals("applied", PlayerDataReceipt.readExisting(file, KEY));
        assertNull(PlayerDataReceipt.readExisting(file, "arcartxsuite:other"));
    }

    @Test
    void rejectsAMissingGzipTrailerEvenWithAReadableMarker() throws IOException {
        Path file = writeSave("applied", true);
        byte[] bytes = Files.readAllBytes(file);
        Files.write(file, Arrays.copyOf(bytes, bytes.length - 4));

        assertThrows(IOException.class, () -> PlayerDataReceipt.readExisting(file, KEY));
    }

    @Test
    void rejectsAnIncompleteInventoryEvenWhenTheMarkerIsAbsent() throws IOException {
        Path file = writeSave(null, false);

        assertThrows(IOException.class, () -> PlayerDataReceipt.readExisting(file, KEY));
    }

    private Path writeSave(String marker, boolean completeInventory) throws IOException {
        Path file = directory.resolve("player.dat");
        try (var output = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
            output.writeByte(10);
            output.writeUTF("");
            output.writeByte(10);
            output.writeUTF("BukkitValues");
            if (marker != null) {
                output.writeByte(8);
                output.writeUTF(KEY);
                output.writeUTF(marker);
            }
            output.writeByte(0);
            output.writeByte(9);
            output.writeUTF("Inventory");
            if (completeInventory) {
                output.writeByte(10);
                output.writeInt(0);
                output.writeByte(0);
            }
        }
        return file;
    }
}
