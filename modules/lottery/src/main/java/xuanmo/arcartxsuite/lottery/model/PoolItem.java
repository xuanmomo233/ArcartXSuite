package xuanmo.arcartxsuite.lottery.model;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * Lottery 奖池中的一个奖品定义。
 *
 * <p>物品来源通过 {@code item.source} 指定，物品标识通过 {@code item.id} 指定。
 * {@link #load(ConfigurationSection, Logger)} 负责从 YAML 配置节读取奖品定义。
 *
 * @param id          奖品标识，缺省时使用配置节名称
 * @param name        奖品显示名称
 * @param delivery    奖品投递方式
 * @param commands    COMMAND 投递方式执行的命令列表
 * @param mailPreset  MAIL 投递方式使用的邮件预设
 * @param itemSource  item.source 对应的物品来源类型
 * @param itemId      item.id 对应的物品标识；MMOItems 使用 TYPE;ID 格式
 * @param itemJson    PLAIN 物品使用的 Base64 序列化 ItemStack 数据
 * @param icon        奖品界面图标定义
 * @param extraCommands 奖品投递成功后执行的附加命令
 * @param amount      发放数量
 * @param weight      奖池内的相对权重
 * @param rarity      CASE 奖池中的稀有度名称
 */
public record PoolItem(
    @NotNull String id,
    @NotNull String name,
    @NotNull DeliveryType delivery,
    @NotNull List<String> commands,
    @Nullable String mailPreset,
    @NotNull PluginItemType itemSource,
    @NotNull String itemId,
    @Nullable String itemJson,
    @Nullable IconDefinition icon,
    @NotNull List<String> extraCommands,
    int amount,
    int weight,
    @Nullable String rarity
) {
    /** 奖品投递方式。 */
    public enum DeliveryType {
        DIRECT, COMMAND, MAIL
    }

    /** item.source 支持的物品来源类型。 */
    public enum PluginItemType {
        MYTHIC, NEIGE, OVERTURE, MMO, PLAIN;

        /**
         * 解析 item.source 的来源类型。
         *
         * <p>可接受的取值包括 MythicMobs、NeigeItems、Overture、MMOItems、Minecraft，
         * 以及对应的 MYTHICMOBS、NEIGEITEMS、MMOITEMS、MINECRAFT、PLAIN 别名。
         *
         * @param value item.source 的配置值
         * @return 对应的物品来源类型
         * @throws IllegalArgumentException value 不是支持的物品来源类型
         */
        public static PluginItemType fromString(String value) {
            if (value == null || value.isBlank()) return PLAIN;
            return switch (value.trim().toUpperCase(Locale.ROOT)) {
                case "MYTHIC", "MYTHICMOBS" -> MYTHIC;
                case "NEIGE", "NEIGEITEMS" -> NEIGE;
                case "OVERTURE" -> OVERTURE;
                case "MMO", "MMOITEMS" -> MMO;
                case "PLAIN", "MINECRAFT" -> PLAIN;
                default -> throw new IllegalArgumentException("未知的 item.source: " + value);
            };
        }
    }

    /**
     * 从 YAML 配置节加载奖品定义。
     *
     * @param section 奖品配置节
     * @param logger  用于记录配置错误的日志记录器
     * @return 加载成功的奖品定义；配置无效时返回 {@code null}
     */
    public static PoolItem load(ConfigurationSection section, Logger logger) {
        String id = section.getString("id", section.getName());
        if (id == null || id.isBlank()) {
            logger.warning("奖品配置 " + section.getName() + " 缺少 id，已跳过");
            return null;
        }

        String name = section.getString("name", id);
        String deliveryStr = section.getString("delivery", "DIRECT");
        DeliveryType delivery = DeliveryType.valueOf(deliveryStr.toUpperCase(Locale.ROOT));
        List<String> commands = section.getStringList("commands");
        String mailPreset = section.getString("mail-preset", "");
        if (mailPreset.isBlank()) mailPreset = null;

        boolean commandOnly = delivery == DeliveryType.COMMAND;
        String itemJson = section.getString("item-json");
        ConfigurationSection itemSection = section.getConfigurationSection("item");
        String source = itemSection == null ? "" : itemSection.getString("source", "");
        String itemId = itemSection == null ? "" : itemSection.getString("id", "");
        if (!commandOnly && source.isBlank() && (itemJson == null || itemJson.isBlank())) {
            logger.warning("奖品 " + id + " 缺少 item.source，已跳过");
            return null;
        }
        PluginItemType itemSource = PluginItemType.fromString(source);
        IconDefinition icon = IconDefinition.load(section, "icon");
        List<String> extraCommands = section.getStringList("extra-commands");
        int amount = section.getInt("amount", 1);
        if (amount <= 0) {
            logger.warning("奖品 " + id + " 的 amount 必须为正数，已跳过");
            return null;
        }
        boolean hasItemJson = itemJson != null && !itemJson.isBlank();
        if (!commandOnly && !hasItemJson) {
            if (itemSource == PluginItemType.PLAIN) {
                String materialName = itemId.startsWith("minecraft:")
                    ? itemId.substring("minecraft:".length()) : itemId;
                if (Material.matchMaterial(materialName, true) == null) {
                    logger.warning("奖品 " + id + " 的 item.id 不是有效的原版物品材质: "
                        + itemId + "，已跳过");
                    return null;
                }
            } else if (itemId.isBlank()) {
                logger.warning("奖品 " + id + " 缺少 item.id，已跳过");
                return null;
            }
        }

        int weight = section.getInt("weight", 1);
        String rarity = section.getString("rarity", null);
        if (weight <= 0) {
            logger.warning("奖品 " + id + " 的 weight 必须为正数，已跳过");
            return null;
        }

        return new PoolItem(id, name, delivery, commands, mailPreset, itemSource, itemId,
            itemJson, icon, extraCommands, amount, weight, rarity);
    }
}
