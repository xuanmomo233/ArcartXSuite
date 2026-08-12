package xuanmo.arcartxsuite.tooltip.service;

import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.capability.TooltipDataCapable;
import xuanmo.arcartxsuite.tooltip.cache.TooltipDataCache;

/**
 * {@link TooltipDataCapable} 能力实现。
 * <p>
 * 提供 tooltip 数据查询和 Lore 注入功能，供 Chat 等模块使用。
 */
public final class TooltipDataCapability implements TooltipDataCapable {

    private final TooltipDataCache cache;

    public TooltipDataCapability(@NotNull TooltipDataCache cache) {
        this.cache = cache;
    }

    @Override
    @NotNull
    public List<String> getTooltipLines(@NotNull Player player) {
        return getTooltipLines(player, player.getInventory().getItemInMainHand());
    }

    @Override
    @NotNull
    public List<String> getTooltipLines(@NotNull Player player, @NotNull ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) return List.of();
        TooltipDataCache.CachedEntry entry = cache.get(player, itemStack);
        return entry == null ? List.of() : entry.textLines();
    }

    @Override
    @NotNull
    public String getStructuredData(@NotNull Player player) {
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        if (itemStack == null || itemStack.getType().isAir()) return "{}";
        TooltipDataCache.CachedEntry entry = cache.get(player, itemStack);
        return entry == null ? "{}" : entry.structuredData();
    }

    @Override
    @NotNull
    public ItemStack injectLore(@NotNull ItemStack itemStack, @NotNull List<String> lines) {
        if (itemStack == null || itemStack.getType().isAir()) return itemStack;
        if (lines == null || lines.isEmpty()) return itemStack;

        try {
            ItemStack clone = itemStack.clone();
            ItemMeta meta = clone.getItemMeta();
            if (meta == null) return itemStack;

            // 将文本行转为 Minecraft 格式的 Lore（带颜色码支持）
            // Bukkit 的 lore 接受带 § 颜色码的字符串
            List<String> lore = new java.util.ArrayList<>(lines.size());
            for (String line : lines) {
                // 转换 & 颜色码为 §
                lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);
            clone.setItemMeta(meta);
            return clone;
        } catch (Exception e) {
            return itemStack;
        }
    }

    @Override
    public boolean hasTooltipData(@NotNull Player player, @NotNull ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) return false;
        return cache.get(player, itemStack) != null;
    }
}
