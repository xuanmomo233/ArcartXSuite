package xuanmo.arcartxsuite.lottery.model;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * Lottery 奖池中的一个奖品定义。
 *
 * <p>物品来源通过 {@code item.source} 指定，物品标识通过 {@code item.id} 指定，
 * 自定义 NBT 通过 {@code item.nbt} 指定（SNBT 字符串，仅 minecraft 源有效）。
 * {@link #load(ConfigurationSection, Logger)} 负责从 YAML 配置节读取奖品定义。
 *
 * @param key          奖品标识，缺省时使用配置节名称
 * @param name         奖品显示名称
 * @param delivery     奖品投递方式
 * @param commands    COMMAND 投递方式执行的命令列表
 * @param mailPresets MAIL 投递方式使用的邮件预设列表
 * @param source       item.source 对应的物品来源类型字符串（minecraft / mythic / neige / overture / mmoitems）
 * @param id           item.id 对应的物品标识；MMOItems 使用 TYPE;ID 格式
 * @param nbt          item.nbt 对应的自定义 NBT（SNBT 字符串，仅 minecraft 源有效）
 * @param icon        奖品界面图标定义
 * @param extraCommands 奖品投递成功后执行的附加命令
 * @param amount      发放数量
 * @param weight      奖池内的相对权重
 * @param rarity      CASE 奖池中的稀有度名称
 */
public record PoolItem(
    @NotNull String key,
    @NotNull String name,
    @NotNull DeliveryType delivery,
    @NotNull List<String> commands,
    @Nullable List<String> mailPresets,
    @NotNull String source,
    @NotNull String id,
    @Nullable String nbt,
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

    /**
     * 从 YAML 配置节加载奖品定义。
     *
     * @param section 奖品配置节
     * @param logger  用于记录配置错误的日志记录器
     * @return 加载成功的奖品定义；配置无效时返回 {@code null}
     */
    public static PoolItem load(ConfigurationSection section, Logger logger) {
        String key = section.getString("id", section.getName());
        if (key == null || key.isBlank()) {
            logger.warning("奖品配置 " + section.getName() + " 缺少 id，已跳过");
            return null;
        }

        String name = section.getString("name", key);
        String deliveryStr = section.getString("delivery", "DIRECT");
        DeliveryType delivery = DeliveryType.valueOf(deliveryStr.toUpperCase(Locale.ROOT));
        List<String> commands = section.getStringList("commands");
        List<String> mailPresets = null;
        ConfigurationSection mailSection = section.getConfigurationSection("mail");
        if (mailSection != null) {
            mailPresets = mailSection.getStringList("presets");
            if (mailPresets.isEmpty()) mailPresets = null;
        }

        boolean commandOnly = delivery == DeliveryType.COMMAND;
        ConfigurationSection itemSection = section.getConfigurationSection("item");
        String source = itemSection == null ? "" : itemSection.getString("source", "");
        String itemId = itemSection == null ? "" : itemSection.getString("id", "");
        String nbt = itemSection == null ? null : itemSection.getString("nbt");
        if (!commandOnly && source.isBlank()) {
            logger.warning("奖品 " + key + " 缺少 item.source，已跳过");
            return null;
        }
        IconDefinition icon = IconDefinition.load(section, "icon");
        List<String> extraCommands = section.getStringList("extra-commands");
        int amount = section.getInt("amount", 1);
        if (amount <= 0) {
            logger.warning("奖品 " + key + " 的 amount 必须为正数，已跳过");
            return null;
        }
        if (!commandOnly && itemId.isBlank()) {
            logger.warning("奖品 " + key + " 缺少 item.id，已跳过");
            return null;
        }

        int weight = section.getInt("weight", 1);
        String rarity = section.getString("rarity", null);
        if (weight <= 0) {
            logger.warning("奖品 " + key + " 的 weight 必须为正数，已跳过");
            return null;
        }

        return new PoolItem(key, name, delivery, commands, mailPresets, source, itemId,
            nbt, icon, extraCommands, amount, weight, rarity);
    }
}
