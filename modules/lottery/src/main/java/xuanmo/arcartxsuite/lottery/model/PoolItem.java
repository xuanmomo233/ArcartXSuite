package xuanmo.arcartxsuite.lottery.model;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * 奖池中的单个奖品定义。
 * <p>
 * 描述奖品的投递方式、物品来源、数量、权重、稀有度等属性，
 * 由 {@link #load} 从 YAML 配置节解析。
 *
 * @param id           奖品唯一标识
 * @param name         显示名称
 * @param delivery     投递方式（直接/命令/邮件）
 * @param commands     命令投递时执行的命令列表
 * @param mailPreset   邮件投递时使用的预设名称
 * @param pluginType   物品来源类型
 * @param pluginId     物品来源 ID（如 Mythic 内部名、原版材料名）
 * @param itemJson     预序列化的 Base64 物品数据，仅 PLAIN 类型使用
 * @param amount       物品数量
 * @param weight       抽取权重
 * @param rarity       稀有度名称，CASE 奖池使用
 */
public record PoolItem(
    @NotNull String id,
    @NotNull String name,
    @NotNull DeliveryType delivery,
    @NotNull List<String> commands,
    @Nullable String mailPreset,
    @NotNull PluginItemType pluginType,
    @NotNull String pluginId,
    @Nullable String itemJson,
    @Nullable IconDefinition icon,
    @NotNull List<String> extraCommands,
    int amount,
    int weight,
    @Nullable String rarity
) {
    /**
     * 奖品投递方式枚举。
     */
    public enum DeliveryType {
        DIRECT, COMMAND, MAIL
    }

    /**
     * 物品来源类型枚举。
     * <p>
     * 配置中可使用插件正式全称（如 {@code MythicMobs}、{@code NeigeItems}、{@code MMOItems}、
     * {@code Overture}、{@code PLAIN}）或旧缩写（{@code MYTHIC}、{@code NEIGE}、{@code MMO}），
     * 通过 {@link #fromString(String)} 统一解析，大小写无关。
     */
    public enum PluginItemType {
        MYTHIC, NEIGE, OVERTURE, MMO, PLAIN;

        /**
         * 将配置字符串解析为枚举值，支持插件正式全称与旧缩写别名，大小写无关。
         *
         * @param value 配置字符串
         * @return 对应枚举值
         * @throws IllegalArgumentException 无法识别时抛出
         */
        public static PluginItemType fromString(String value) {
            if (value == null || value.isBlank()) {
                return PLAIN;
            }
            String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
            return switch (normalized) {
                case "MYTHIC", "MYTHICMOBS" -> MYTHIC;
                case "NEIGE", "NEIGEITEMS" -> NEIGE;
                case "OVERTURE" -> OVERTURE;
                case "MMO", "MMOITEMS" -> MMO;
                case "PLAIN" -> PLAIN;
                default -> throw new IllegalArgumentException("未知的 plugin-type: " + value);
            };
        }
    }

    /**
     * 从 YAML 配置节加载奖品定义。
     *
     * @param section 配置节
     * @param logger  日志记录器
     * @return 解析后的 {@link PoolItem} 实例，缺少 id 或配置无效时返回 {@code null}
     */
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
        PluginItemType pluginType = PluginItemType.fromString(pluginTypeStr);
        String pluginId = section.getString("plugin-id", "");
        String itemJson = section.getString("item-json");
        IconDefinition icon = IconDefinition.load(section, "icon");
        List<String> extraCommands = section.getStringList("extra-commands");
        int amount = section.getInt("amount", 1);
        if (amount <= 0) {
            logger.warning("奖品 " + id + " 的 amount 必须为正数，已跳过");
            return null;
        }
        boolean commandOnly = delivery == DeliveryType.COMMAND;
        if (!commandOnly && pluginType == PluginItemType.PLAIN && (itemJson == null || itemJson.isBlank())) {
            String materialName = pluginId.startsWith("minecraft:")
                ? pluginId.substring("minecraft:".length()) : pluginId;
            if (Material.matchMaterial(materialName, true) == null) {
                logger.warning("奖品 " + id + " 使用了无效材质: " + pluginId + "，已跳过");
                return null;
            }
        } else if (!commandOnly && (itemJson == null || itemJson.isBlank()) && pluginId.isBlank()) {
            logger.warning("奖品 " + id + " 缺少 plugin-id，已跳过");
            return null;
        }

        int weight = section.getInt("weight", 1);
        String rarity = section.getString("rarity", null);

        if (weight <= 0) {
            logger.warning("奖品 " + id + " 的 weight 必须为正数，已跳过");
            return null;
        }

        return new PoolItem(id, name, delivery, commands, mailPreset, pluginType, pluginId,
            itemJson, icon, extraCommands, amount, weight, rarity);
    }
}
