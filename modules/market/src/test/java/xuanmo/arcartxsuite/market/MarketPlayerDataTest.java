package xuanmo.arcartxsuite.market;
import xuanmo.arcartxsuite.api.inventory.PlayerDataReceipt;

import java.io.DataOutputStream;
import java.io.EOFException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketPlayerDataTest {
    private static final String KEY = "arcartxsuite:market_item_batch";

    @TempDir Path directory;

    @Test
    void readsThePlayerReceiptEvenWhenAnItemContainsTheSameKey() throws Exception {
        Path file = writePlayerData("player-receipt");
        assertEquals("player-receipt", PlayerDataReceipt.read(file, KEY));
    }

    @Test
    void anItemReceiptOrMissingPlayerFileDoesNotAcknowledgeDelivery() throws Exception {
        assertNull(PlayerDataReceipt.read(writePlayerData(null), KEY));
        assertNull(PlayerDataReceipt.read(directory.resolve("missing.dat"), KEY));
    }

    @Test
    void aTruncatedPlayerReceiptCannotAcknowledgeDelivery() throws Exception {
        Path file = directory.resolve("incomplete.dat");
        try (var output = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
            tag(output, 10, "");
            tag(output, 10, "BukkitValues");
            tag(output, 8, KEY);
            output.writeShort(16);
            output.writeByte('x');
        }
        assertThrows(EOFException.class, () -> PlayerDataReceipt.read(file, KEY));
    }

    private Path writePlayerData(String receipt) throws Exception {
        Path file = directory.resolve("player.dat");
        try (var output = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
            tag(output, 10, "");
            tag(output, 9, "Inventory");
            output.writeByte(10);
            output.writeInt(1);
            tag(output, 10, "BukkitValues");
            tag(output, 8, KEY);
            output.writeUTF("item-receipt");
            output.writeByte(0);
            output.writeByte(0);
            tag(output, 10, "BukkitValues");
            if (receipt != null) {
                tag(output, 8, KEY);
                output.writeUTF(receipt);
            }
            output.writeByte(0);
            output.writeByte(0);
        }
        return file;
    }

    private static void tag(DataOutputStream output, int type, String name) throws Exception {
        output.writeByte(type);
        output.writeUTF(name);
    }
}
