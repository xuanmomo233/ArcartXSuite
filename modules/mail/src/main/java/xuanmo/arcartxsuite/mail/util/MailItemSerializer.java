package xuanmo.arcartxsuite.mail.util;

import java.io.IOException;
import java.util.Base64;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.util.ItemSerializer;

public final class MailItemSerializer {

    private MailItemSerializer() {
    }

    public static String serialize(ItemStack itemStack) throws IOException {
        return Base64.getEncoder().encodeToString(ItemSerializer.serialize(itemStack));
    }

    public static ItemStack deserialize(String encoded) throws IOException {
        byte[] data = Base64.getDecoder().decode(encoded);
        try {
            ItemStack itemStack = ItemSerializer.deserialize(data);
            if (itemStack != null) {
                return itemStack;
            }
            throw new IOException("解码后的对象不是 ItemStack。");
        } catch (RuntimeException exception) {
            throw new IOException("反序列化 ItemStack 失败。", exception);
        }
    }
}
