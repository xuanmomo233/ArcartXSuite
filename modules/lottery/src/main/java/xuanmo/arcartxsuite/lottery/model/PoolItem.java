package xuanmo.arcartxsuite.lottery.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PoolItem(
    @NotNull String id,
    @NotNull String name,
    @NotNull DeliveryType delivery,
    @NotNull List<String> commands,
    @Nullable String mailPreset,
    @NotNull PluginItemType pluginType,
    @NotNull String pluginId,
    @Nullable String itemJson,
    int amount,
    int weight,
    boolean stattrakEnabled,
    @Nullable String rarity,
    @Nullable Map<String, WearRange> wearDistribution
) {
    public enum DeliveryType {
        DIRECT, COMMAND, MAIL
    }

    public enum PluginItemType {
        MYTHIC, NEIGE, OVERTURE, MMO, PLAIN
    }

    public record WearRange(
        double min,
        double max,
        double weight
    ) {}

    public static PoolItem load(ConfigurationSection section, Logger logger) {
        String id = section.getString("id");
        if (id == null || id.isBlank()) {
            logger.warning("奖品配置缺少 id 字段，已跳过");
            return null;
        }

        String name = section.getString("name", id);
        String deliveryStr = section.getString("delivery", "DIRECT");
        DeliveryType delivery = DeliveryType.valueOf(deliveryStr.toUpperCase());
        List<String> commands = section.getStringList("commands");
        String mailPreset = section.getString("mail-preset", "");
        if (mailPreset.isBlank()) mailPreset = null;

        String pluginTypeStr = section.getString("plugin-type", "PLAIN");
        PluginItemType pluginType = PluginItemType.valueOf(pluginTypeStr.toUpperCase());
        String pluginId = section.getString("plugin-id", "");
        String itemJson = section.getString("item-json");
        int amount = section.getInt("amount", 1);
        if (amount <= 0) {
            logger.warning("奖品 " + id + " 的 amount 必须为正数，已跳过");
            return null;
        }
        if (pluginType == PluginItemType.PLAIN && (itemJson == null || itemJson.isBlank())) {
            String materialName = pluginId.startsWith("minecraft:")
                ? pluginId.substring("minecraft:".length()) : pluginId;
            if (Material.matchMaterial(materialName, true) == null) {
                logger.warning("奖品 " + id + " 的原版物品无效: " + pluginId + "，已跳过");
                return null;
            }
        } else if (pluginId.isBlank()) {
            logger.warning("奖品 " + id + " 缺少 plugin-id，已跳过");
            return null;
        }

        int weight = section.getInt("weight", 1);
        boolean stattrak = section.getBoolean("stattrak-enabled", false);
        String rarity = section.getString("rarity", null);

        Map<String, WearRange> wearDist = null;
        ConfigurationSection wearSection = section.getConfigurationSection("wear-distribution");
        if (wearSection != null) {
            wearDist = new java.util.LinkedHashMap<>();
            for (String key : wearSection.getKeys(false)) {
                ConfigurationSection ws = wearSection.getConfigurationSection(key);
                if (ws == null) continue;
                wearDist.put(key, new WearRange(
                    ws.getDouble("min", 0.0),
                    ws.getDouble("max", 1.0),
                    ws.getDouble("weight", 1.0)
                ));
            }
        }

        if (weight <= 0) {
            logger.warning("奖品 " + id + " 的 weight 必须为正数，已跳过");
            return null;
        }

        return new PoolItem(id, name, delivery, commands, mailPreset, pluginType, pluginId,
            itemJson, amount, weight, stattrak, rarity, wearDist);
    }
}
