package xuanmo.arcartxsuite.chat.service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.chat.model.RedPacketPoolEntry;
import xuanmo.arcartxsuite.chat.model.RedPacketPreset;
import xuanmo.arcartxsuite.chat.model.RedPacketReward;

/**
 * 红包预设加载器，从 {@code chat/redpackets/} 目录的 yml 文件加载奖励池红包定义。
 * <p>
 * 物品奖励在加载时解析为 ItemStack 并序列化为 Base64，保证发出的红包内容不受后续预设改动影响。
 */
public final class RedPacketPresetLoader {

    private RedPacketPresetLoader() {
    }

    /**
     * 加载目录下全部红包预设。
     *
     * @param directory             预设目录
     * @param logger                日志
     * @param itemSourceRegistry    物品来源注册表（可为 null）
     * @param iconResolver          图标解析器（可为 null，优先于 itemSourceRegistry）
     * @param vanillaItemNameBridge 原版物品名桥接（可为 null，用于生成物品描述）
     * @return 预设映射（id → 定义）
     */
    public static Map<String, RedPacketPreset> loadPresets(File directory, Logger logger,
            @Nullable ItemSourceRegistry itemSourceRegistry,
            @Nullable IconResolver iconResolver,
            @Nullable VanillaItemNameBridge vanillaItemNameBridge) {
        Map<String, RedPacketPreset> result = new LinkedHashMap<>();
        if (directory == null || !directory.exists()) {
            return Map.of();
        }
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return Map.of();
        }
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        for (File file : files) {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection presetSection = configuration.getConfigurationSection("preset");
            if (presetSection == null) {
                continue;
            }
            String id = string(presetSection.getString("id", file.getName().replace(".yml", ""))).toLowerCase(Locale.ROOT);
            boolean enabled = presetSection.getBoolean("enabled", true);
            // 显示名/祝福语/奖励描述为管理员配置文本，加载时翻译 & 颜色码
            String displayName = MessageProvider.translateColors(string(presetSection.getString("display-name", id)));
            String wish = MessageProvider.translateColors(string(presetSection.getString("wish", "恭喜发财，大吉大利")));
            int count = Math.max(1, presetSection.getInt("count", 5));
            boolean replacement = presetSection.getBoolean("replacement", false);
            long expireMinutes = presetSection.getLong("expire-minutes", 0L);

            List<RedPacketPoolEntry> rewards = loadRewards(presetSection, logger, id,
                itemSourceRegistry, iconResolver, vanillaItemNameBridge);
            if (rewards.isEmpty()) {
                logger.warning("红包预设 '" + id + "' 没有任何有效奖励，已跳过。");
                continue;
            }
            if (!replacement) {
                int totalSlots = 0;
                for (RedPacketPoolEntry entry : rewards) {
                    totalSlots += Math.max(1, entry.quantity());
                }
                if (count > totalSlots) {
                    logger.warning("红包预设 '" + id + "' 份数(" + count + ")超过无放回奖励池总量(" + totalSlots + ")，已按池量收缩。");
                    count = totalSlots;
                }
            }
            result.put(id, new RedPacketPreset(id, enabled, displayName, wish, count, replacement, expireMinutes, rewards));
        }
        return Map.copyOf(result);
    }

    private static List<RedPacketPoolEntry> loadRewards(ConfigurationSection presetSection, Logger logger, String presetId,
            @Nullable ItemSourceRegistry itemSourceRegistry,
            @Nullable IconResolver iconResolver,
            @Nullable VanillaItemNameBridge vanillaItemNameBridge) {
        List<RedPacketPoolEntry> rewards = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> rawReward : presetSection.getMapList("rewards")) {
            String type = mapString(rawReward, "type", "").trim().toLowerCase(Locale.ROOT);
            int weight = Math.max(1, parseInt(mapString(rawReward, "weight", "1"), 1));
            int quantity = parseInt(mapString(rawReward, "quantity", mapString(rawReward, "max-claims", "1")), 1);
            String description = MessageProvider.translateColors(mapString(rawReward, "description", ""));

            RedPacketReward reward = switch (type) {
                case "currency" -> {
                    String currencyId = mapString(rawReward, "currency", "money").trim().toLowerCase(Locale.ROOT);
                    BigDecimal amount = parseAmount(mapString(rawReward, "amount", "0"));
                    if (amount.signum() <= 0) {
                        logger.warning("红包预设 '" + presetId + "' 的货币奖励金额无效，已跳过。");
                        yield null;
                    }
                    String desc = description.isBlank() ? amount.stripTrailingZeros().toPlainString() + " " + currencyId : description;
                    yield RedPacketReward.currency(currencyId, amount, desc);
                }
                case "item" -> {
                    ItemStack itemStack = generateItemStack(rawReward, itemSourceRegistry, iconResolver, logger, presetId);
                    if (itemStack == null) {
                        yield null;
                    }
                    String itemData = java.util.Base64.getEncoder().encodeToString(ItemSerializer.serialize(itemStack));
                    String desc = description.isBlank() ? resolveDescription(itemStack, vanillaItemNameBridge) : description;
                    yield RedPacketReward.item(itemData, desc);
                }
                case "command" -> {
                    String command = mapString(rawReward, "command", "");
                    if (command.isBlank()) {
                        logger.warning("红包预设 '" + presetId + "' 的指令奖励缺少 command 字段，已跳过。");
                        yield null;
                    }
                    String desc = description.isBlank() ? "指令奖励" : description;
                    yield RedPacketReward.command(command, desc);
                }
                default -> {
                    logger.warning("红包预设 '" + presetId + "' 包含未知奖励类型 '" + type + "'，已跳过。");
                    yield null;
                }
            };
            if (reward != null) {
                rewards.add(new RedPacketPoolEntry(reward, weight, quantity));
                index++;
            }
        }
        return List.copyOf(rewards);
    }

    /**
     * 按 source + id + 附加属性生成物品堆。
     * <p>
     * 优先 {@link IconResolver}（支持 material/custom-model-data/texture/name/lore/nbt 等全属性），
     * 回退 {@link ItemSourceRegistry}，最后回退原版材质。
     */
    @Nullable
    private static ItemStack generateItemStack(Map<?, ?> rawItem,
            @Nullable ItemSourceRegistry itemSourceRegistry,
            @Nullable IconResolver iconResolver, Logger logger, String presetId) {
        String source = mapString(rawItem, "source", "minecraft").trim().toLowerCase(Locale.ROOT);
        String itemId = mapString(rawItem, "id", "");
        int amount = Math.max(1, parseInt(mapString(rawItem, "amount", "1"), 1));
        if (itemId.isBlank()) {
            logger.warning("红包预设 '" + presetId + "' 的物品奖励缺少 id 字段，已跳过。");
            return null;
        }
        try {
            if (iconResolver != null) {
                IconDefinition icon = iconFromMap(source, itemId, amount, rawItem);
                if (icon != null && icon.hasIcon()) {
                    ItemStack resolved = iconResolver.resolveItemStack(null, icon);
                    if (resolved != null && !resolved.getType().isAir()) {
                        return resolved;
                    }
                }
            }
            if (itemSourceRegistry != null) {
                ItemStack item = itemSourceRegistry.generateItem(source, itemId, amount, null, null);
                if (item != null) {
                    return item;
                }
            }
            Material material = Material.matchMaterial(itemId, true);
            if (material != null && !material.isAir()) {
                return new ItemStack(material, amount);
            }
            logger.warning("红包预设 '" + presetId + "' 的物品奖励无法生成: " + source + ":" + itemId);
            return null;
        } catch (Exception exception) {
            logger.warning("红包预设 '" + presetId + "' 的物品奖励生成异常: " + source + ":" + itemId + " | " + exception.getMessage());
            return null;
        }
    }

    /** 从 Map 构造 {@link IconDefinition}，字段与 {@link IconDefinition#load(ConfigurationSection)} 兼容。 */
    @Nullable
    private static IconDefinition iconFromMap(String source, String itemId, int amount, Map<?, ?> rawItem) {
        String name = mapString(rawItem, "name", "");
        int customModelData = rawItem.get("custom-model-data") instanceof Number cmd ? cmd.intValue()
            : rawItem.get("customModelData") instanceof Number cmd2 ? cmd2.intValue() : 0;
        List<String> lore = new ArrayList<>();
        Object rawLore = rawItem.get("lore");
        if (rawLore instanceof List<?> loreList) {
            for (Object line : loreList) {
                lore.add(String.valueOf(line));
            }
        }
        Map<String, String> nbt = new LinkedHashMap<>();
        String nbtString = "";
        Object rawNbt = rawItem.get("nbt");
        if (rawNbt instanceof Map<?, ?> nbtMap) {
            for (Map.Entry<?, ?> entry : nbtMap.entrySet()) {
                nbt.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        } else if (rawNbt instanceof String s) {
            nbtString = s;
        }
        String mmoType = mapString(rawItem, "mmo-type", "");
        String mmoId = mapString(rawItem, "mmo-id", "");
        // minecraft/vanilla 源走 material 路径，以便应用 name/lore/custom-model-data 等属性；
        // 外部物品源（mythic/neige/overture/mmo）填 source+sourceId，由 IconResolver 生成。
        boolean vanilla = source.isBlank() || "minecraft".equals(source)
            || "plain".equals(source) || "vanilla".equals(source);
        IconDefinition icon = new IconDefinition(
            vanilla ? itemId : "", amount, name, lore, customModelData,
            vanilla ? "" : source, vanilla ? "" : itemId, mmoType, mmoId, "",
            mapString(rawItem, "texture", ""), mapString(rawItem, "texture-url", ""),
            nbt, nbtString, rawItem.get("glow") instanceof Boolean g && g,
            mapString(rawItem, "skull-texture", ""), mapString(rawItem, "color", "")
        );
        return icon.hasIcon() ? icon : null;
    }

    private static String resolveDescription(ItemStack itemStack, @Nullable VanillaItemNameBridge bridge) {
        String name;
        if (bridge != null) {
            name = bridge.resolveDisplayName(itemStack);
        } else {
            ItemMeta meta = itemStack.getItemMeta();
            name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : itemStack.getType().name();
        }
        return name + " x" + itemStack.getAmount();
    }

    private static String string(String value) {
        return value == null ? "" : value.trim();
    }

    private static String mapString(Map<?, ?> values, String key, String defaultValue) {
        Object value = values.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static int parseInt(String rawValue, int defaultValue) {
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static BigDecimal parseAmount(String rawValue) {
        try {
            return new BigDecimal(rawValue.trim());
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }
}
