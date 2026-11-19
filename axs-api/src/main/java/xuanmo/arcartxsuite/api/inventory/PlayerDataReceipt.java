package xuanmo.arcartxsuite.api.inventory;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** Read the Bukkit persistent string stored alongside the player's inventory. */
public final class PlayerDataReceipt {
    private PlayerDataReceipt() {
    }

    /** Request a player save on the server thread. Read the file to confirm that it finished. */
    public static void save(Player player, NamespacedKey key, String token) {
        player.getPersistentDataContainer().set(key, PersistentDataType.STRING, token);
        player.saveData();
    }

    public static String read(Path file, String key) throws IOException {
        try {
            return readExisting(file, key);
        } catch (NoSuchFileException exception) {
            return null;
        }
    }

    /** Returns a marker only after validating the complete compressed player save. */
    public static String readExisting(Path file, String key) throws IOException {
        try (var fileStream = Files.newInputStream(file);
             var input = new DataInputStream(new GZIPInputStream(fileStream))) {
            if (input.readUnsignedByte() != 10) throw new IOException("玩家存档没有根复合标签");
            input.readUTF();
            String marker = null;
            boolean foundValues = false;
            for (int tag; (tag = input.readUnsignedByte()) != 0; ) {
                String name = input.readUTF();
                if ("BukkitValues".equals(name)) {
                    if (tag != 10 || foundValues) throw new IOException("玩家存档的持久数据标签无效");
                    foundValues = true;
                    for (int entry; (entry = input.readUnsignedByte()) != 0; ) {
                        if (key.equals(input.readUTF())) {
                            if (entry != 8 || marker != null) throw new IOException("玩家存档的回执标签无效");
                            marker = input.readUTF();
                        } else {
                            skip(input, entry, 1);
                        }
                    }
                } else {
                    skip(input, tag, 1);
                }
            }
            // Reading past the root also verifies the GZIP trailer and CRC.
            if (input.read() != -1) throw new IOException("玩家存档的根标签之后还有数据");
            return marker;
        }
    }

    private static void skip(DataInputStream input, int tag, int depth) throws IOException {
        if (depth > 64) throw new IOException("玩家存档标签嵌套过深");
        switch (tag) {
            case 1 -> input.skipNBytes(1);
            case 2 -> input.skipNBytes(2);
            case 3, 5 -> input.skipNBytes(4);
            case 4, 6 -> input.skipNBytes(8);
            case 7 -> input.skipNBytes(length(input));
            case 8 -> input.skipNBytes(input.readUnsignedShort());
            case 9 -> {
                int type = input.readUnsignedByte();
                int count = length(input);
                for (int index = 0; index < count; index++) skip(input, type, depth + 1);
            }
            case 10 -> {
                for (int entry; (entry = input.readUnsignedByte()) != 0; ) {
                    input.skipNBytes(input.readUnsignedShort());
                    skip(input, entry, depth + 1);
                }
            }
            case 11 -> input.skipNBytes(4L * length(input));
            case 12 -> input.skipNBytes(8L * length(input));
            default -> throw new IOException("玩家存档包含未知标签 " + tag);
        }
    }

    private static int length(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size < 0 || size > 16_777_216) throw new IOException("玩家存档标签长度无效");
        return size;
    }
}
