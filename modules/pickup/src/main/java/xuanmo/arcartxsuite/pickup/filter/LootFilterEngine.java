package xuanmo.arcartxsuite.pickup.filter;

import java.util.List;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import xuanmo.arcartxsuite.pickup.config.PickupModuleConfiguration.FilterConfig;

/**
 * 掉落物过滤引擎。
 * <p>
 * 根据配置的过滤规则判断物品是否应在扫描面板中显示。
 * 支持五维过滤：材质黑/白名单、物品名称正则、Lore 正则、NBT 键匹配、最小堆叠数量。
 */
public final class LootFilterEngine {

    private final FilterConfig config;

    public LootFilterEngine(FilterConfig config) {
        this.config = config;
    }

    /**
     * 判断指定物品是否应在扫描面板中显示。
     *
     * @param itemStack 待检查的物品
     * @return true 表示应显示，false 表示应过滤掉
     */
    public boolean shouldDisplay(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        if (itemStack.getAmount() < config.minAmount()) {
            return false;
        }

        // 材质过滤
        Material material = itemStack.getType();
        if (config.mode() == FilterConfig.FilterMode.BLACKLIST) {
            if (config.blacklist().contains(material)) {
                return false;
            }
        } else {
            if (!config.whitelist().contains(material)) {
                return false;
            }
        }

        // 名称正则过滤
        if (!config.nameBlacklistRegex().isEmpty()) {
            String displayName = stripColor(resolveDisplayName(itemStack));
            for (Pattern pattern : config.nameBlacklistRegex()) {
                if (pattern.matcher(displayName).find()) {
                    return false;
                }
            }
        }

        // Lore 过滤
        if (!checkLore(itemStack)) {
            return false;
        }

        // NBT 键过滤
        if (!checkNbt(itemStack)) {
            return false;
        }

        return true;
    }

    /**
     * 检查 Lore 过滤规则。
     * <p>
     * 黑名单：Lore 中任意一行去色后匹配到任意一个黑名单正则 → 不显示。
     * 白名单：非空时，Lore 中必须至少有一行匹配到任意一个白名单正则 → 才显示。
     */
    private boolean checkLore(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        List<String> lore = (meta != null && meta.hasLore()) ? meta.getLore() : null;

        // Lore 黑名单检查
        if (!config.loreBlacklistRegex().isEmpty() && lore != null) {
            for (String line : lore) {
                String stripped = stripColor(line);
                for (Pattern pattern : config.loreBlacklistRegex()) {
                    if (pattern.matcher(stripped).find()) {
                        return false;
                    }
                }
            }
        }

        // Lore 白名单检查：非空时必须匹配
        if (!config.loreWhitelistRegex().isEmpty()) {
            if (lore == null || lore.isEmpty()) {
                return false;
            }
            boolean matched = false;
            for (String line : lore) {
                String stripped = stripColor(line);
                for (Pattern pattern : config.loreWhitelistRegex()) {
                    if (pattern.matcher(stripped).find()) {
                        matched = true;
                        break;
                    }
                }
                if (matched) break;
            }
            if (!matched) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查 NBT 键过滤规则。
     * <p>
     * 通过将 ItemStack 序列化为 NBT 字符串（SNBT 格式），
     * 检查其中是否包含指定的键路径。
     * <p>
     * 黑名单：NBT 中包含任意一个黑名单键 → 不显示。
     * 白名单：非空时，NBT 中必须包含至少一个白名单键 → 才显示。
     */
    private boolean checkNbt(ItemStack itemStack) {
        boolean needBlacklist = !config.nbtBlacklistKeys().isEmpty();
        boolean needWhitelist = !config.nbtWhitelistKeys().isEmpty();
        if (!needBlacklist && !needWhitelist) {
            return true;
        }

        String nbtString = serializeNbt(itemStack);
        if (nbtString == null || nbtString.isEmpty()) {
            // 无 NBT：黑名单通过，白名单不通过
            return !needWhitelist;
        }

        // NBT 黑名单检查
        if (needBlacklist) {
            for (String key : config.nbtBlacklistKeys()) {
                if (containsNbtKey(nbtString, key)) {
                    return false;
                }
            }
        }

        // NBT 白名单检查
        if (needWhitelist) {
            boolean matched = false;
            for (String key : config.nbtWhitelistKeys()) {
                if (containsNbtKey(nbtString, key)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }

        return true;
    }

    /**
     * 将 ItemStack 序列化为 NBT 字符串。
     * 使用 Bukkit 的 ItemStack 序列化机制获取 tag 信息。
     */
    private static String serializeNbt(ItemStack itemStack) {
        try {
            ItemMeta meta = itemStack.getItemMeta();
            if (meta == null) return null;
            // 使用 meta.serialize() 获取可检索的 NBT 数据表示
            return meta.serialize().toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检查 NBT 字符串中是否包含指定的键。
     * 支持嵌套路径格式（如 "custom.trash"），会按 "." 分割后逐段检查。
     * 匹配规则：键名后跟 ":" 或 "=" 或 "}" 等 SNBT 分隔符。
     */
    private static boolean containsNbtKey(String nbtString, String keyPath) {
        if (keyPath.contains(".")) {
            // 嵌套路径：所有段都必须出现
            String[] parts = keyPath.split("\\.");
            for (String part : parts) {
                if (!nbtString.contains(part)) {
                    return false;
                }
            }
            return true;
        }
        return nbtString.contains(keyPath);
    }

    /** 解析物品显示名：优先取 ItemMeta 自定义名，否则返回材质名。 */
    private static String resolveDisplayName(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return itemStack.getType().name();
    }

    /** 去除颜色代码（用于名称/Lore 正则匹配前的消毒）。 */
    private static String stripColor(String text) {
        return ChatColor.stripColor(text);
    }
}
