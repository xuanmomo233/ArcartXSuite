package xuanmo.arcartxsuite.mail.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.inventory.ItemStack;

/** 与仓库一致：图标只展示一个物品，真实数量通过独立数据和文本展示。 */
public final class MailItemPreview {

    private MailItemPreview() {
    }

    public static ItemStack copyForIcon(ItemStack item) {
        return xuanmo.arcartxsuite.api.util.ItemPreview.copyForIcon(item);
    }

    public static Map<String, Object> amounts(List<ItemStack> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                ItemStack item = items.get(i);
                if (item != null && !item.getType().isAir()) {
                    result.put(Integer.toString(i), item.getAmount());
                }
            }
        }
        return result;
    }
}
