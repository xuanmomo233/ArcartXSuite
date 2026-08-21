package xuanmo.arcartxsuite.pickup.filter;

import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.item.ItemMatcher;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.pickup.config.PickupModuleConfiguration.FilterConfig;

/**
 * 掉落物过滤引擎。
 * <p>
 * 根据配置的过滤规则判断物品是否应在扫描面板中显示。
 * 各维度通过独立的 {@link ItemMatcher} 实现，调用 {@link ItemMatcherAPI#matches} 判断。
 * 支持五维过滤：材质黑/白名单、物品名称正则、Lore 正则、NBT 键匹配、最小堆叠数量。
 */
public final class LootFilterEngine {

    private final FilterConfig config;
    private final ItemMatcherAPI itemMatcher;

    public LootFilterEngine(FilterConfig config, ItemMatcherAPI itemMatcher) {
        this.config = config;
        this.itemMatcher = itemMatcher;
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
        if (config.mode() == FilterConfig.FilterMode.BLACKLIST) {
            if (itemMatcher.matches(config.materialBlacklist(), itemStack)) {
                return false;
            }
        } else {
            // WHITELIST 模式：非空白名单时，未命中则排除
            if (!config.materialWhitelist().emptyMatcher()
                && !itemMatcher.matches(config.materialWhitelist(), itemStack)) {
                return false;
            }
        }

        // 名称黑名单正则过滤
        if (itemMatcher.matches(config.nameBlacklist(), itemStack)) {
            return false;
        }

        // Lore 黑名单过滤
        if (itemMatcher.matches(config.loreBlacklist(), itemStack)) {
            return false;
        }

        // Lore 白名单过滤：非空时必须匹配
        if (!config.loreWhitelist().emptyMatcher()
            && !itemMatcher.matches(config.loreWhitelist(), itemStack)) {
            return false;
        }

        // NBT 键黑名单过滤
        if (itemMatcher.matches(config.nbtBlacklist(), itemStack)) {
            return false;
        }

        // NBT 键白名单过滤：非空时必须包含
        if (!config.nbtWhitelist().emptyMatcher()
            && !itemMatcher.matches(config.nbtWhitelist(), itemStack)) {
            return false;
        }

        return true;
    }
}
