package xuanmo.arcartxsuite.fishing.reward;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * 等级奖励配置加载器，从 {@code rewards.yml} 解析为奖励定义列表。
 * <p>
 * 关联模块：{@code fishing}。
 */
public final class RewardLoader {

    private RewardLoader() {}

    /**
     * 从 YAML 配置加载等级奖励列表。
     *
     * @param yaml   rewards.yml 配置
     * @param logger 日志记录器
     * @return 奖励定义列表（按等级升序）
     */
    public static @NotNull List<SeasonRewardDefinition> load(@NotNull YamlConfiguration yaml, @NotNull Logger logger) {
        List<SeasonRewardDefinition> rewards = new ArrayList<>();
        ConfigurationSection rewardsSection = yaml.getConfigurationSection("rewards");
        if (rewardsSection == null) return rewards;

        for (String id : rewardsSection.getKeys(false)) {
            ConfigurationSection section = rewardsSection.getConfigurationSection(id);
            if (section == null) continue;
            try {
                rewards.add(parseReward(id, section));
            } catch (Exception e) {
                logger.warning("解析等级奖励失败 [" + id + "]: " + e.getMessage());
            }
        }
        rewards.sort((a, b) -> Integer.compare(a.level(), b.level()));
        return rewards;
    }

    private static SeasonRewardDefinition parseReward(String id, ConfigurationSection section) {
        int level = section.getInt("level", 1);
        String type = section.getString("type", "item");
        String iconMaterial = section.getString("icon.material", "STONE");
        String iconName = section.getString("icon.name", "");
        int iconAmount = section.getInt("icon.amount", 1);

        String itemSource = null, itemId = null, itemNbt = null;
        int itemAmount = 0;
        if ("item".equals(type)) {
            itemSource = section.getString("item.source", "minecraft");
            itemId = section.getString("item.id", "STONE");
            itemAmount = section.getInt("item.amount", 1);
            itemNbt = section.getString("item.nbt", "");
        }

        String currencyId = null;
        double currencyAmount = 0;
        if ("currency".equals(type)) {
            currencyId = section.getString("currency.currency-id", "money");
            currencyAmount = section.getDouble("currency.amount", 0);
        }

        List<String> commands = null;
        if ("command".equals(type)) {
            commands = section.getStringList("commands");
        }

        List<String> mailPresets = null;
        if ("mail".equals(type)) {
            ConfigurationSection mailSection = section.getConfigurationSection("mail");
            if (mailSection != null) {
                mailPresets = mailSection.getStringList("presets");
            }
            if (mailPresets == null || mailPresets.isEmpty()) {
                mailPresets = java.util.List.of();
            }
        }

        return new SeasonRewardDefinition(id, level, type, iconMaterial, iconName, iconAmount,
            itemSource, itemId, itemAmount, itemNbt, currencyId, currencyAmount, commands, mailPresets);
    }
}
