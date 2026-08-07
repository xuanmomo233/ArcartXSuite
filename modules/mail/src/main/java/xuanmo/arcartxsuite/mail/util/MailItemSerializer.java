package xuanmo.arcartxsuite.mail.util;

import java.io.IOException;
import java.util.Base64;
import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.util.ItemSerializer;

/**
 * 邮件物品序列化工具，将 {@link ItemStack} 与 Base64 字符串互转，用于附件持久化。
 * <p>
 * 关联模块：mail。委托 {@link ItemSerializer} 完成底层序列化。
 */
public final class MailItemSerializer {

    private MailItemSerializer() {
    }

    /**
     * 将物品堆叠序列化为 Base64 字符串。
     *
     * @param itemStack 物品堆叠
     * @return Base64 编码字符串
     * @throws IOException 序列化失败时抛出
     */
    public static String serialize(ItemStack itemStack) throws IOException {
        return Base64.getEncoder().encodeToString(ItemSerializer.serialize(itemStack));
    }

    /**
     * 将 Base64 字符串反序列化为物品堆叠。
     *
     * @param encoded Base64 编码字符串
     * @return 物品堆叠
     * @throws IOException 解码或反序列化失败时抛出
     */
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
