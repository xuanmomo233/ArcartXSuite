package xuanmo.arcartxsuite.fishing.settlement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.fishing.reward.SeasonRewardDefinition;

/**
 * 赛季结算配置加载器，从 {@code settlement.yml} 解析为结算条目列表。
 * <p>
 * 关联模块：{@code fishing}。
 */
public final class SettlementLoader {

    private SettlementLoader() {}

    /**
     * 从 YAML 配置加载结算条目列表。
     *
     * @param yaml   settlement.yml 配置
     * @param logger 日志记录器
     * @return 结算条目列表
     */
    public static @NotNull List<SettlementDefinition> load(@NotNull YamlConfiguration yaml, @NotNull Logger logger) {
        List<SettlementDefinition> settlements = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("settlements");
        if (section == null) return settlements;

        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) continue;
            try {
                String condition = entry.getString("condition", "true");
                String description = entry.getString("description", "");
                List<SeasonRewardDefinition> rewards = new ArrayList<>();
                List<?> rawRewards = entry.getList("rewards");
                if (rawRewards != null) {
                    int idx = 0;
                    for (Object item : rawRewards) {
                        if (item instanceof Map<?, ?> map) {
                            SeasonRewardDefinition reward = parseRewardFromMap(id + "_reward_" + idx, map);
                            if (reward != null) rewards.add(reward);
                        }
                        idx++;
                    }
                }
                settlements.add(new SettlementDefinition(id, condition, description, rewards));
            } catch (Exception e) {
                logger.warning("解析结算条目失败 [" + id + "]: " + e.getMessage());
            }
        }
        return settlements;
    }

    @SuppressWarnings("unchecked")
    private static SeasonRewardDefinition parseRewardFromMap(String id, Map<?, ?> map) {
        String type = strOr(map.get("type"), "item");
        String iconMaterial = "STONE";
        String iconName = "";
        int iconAmount = 1;
        String itemSource = null, itemId = null, itemNbt = null;
        int itemAmount = 0;
        String currencyId = null;
        double currencyAmount = 0;
        List<String> commands = null;
        String mailPresetId = null;

        if ("item".equals(type)) {
            Object itemObj = map.get("item");
            if (itemObj instanceof Map<?, ?> itemMap) {
                itemSource = strOr(itemMap.get("source"), "minecraft");
                itemId = strOr(itemMap.get("id"), "STONE");
                itemAmount = itemMap.get("amount") != null ? ((Number) itemMap.get("amount")).intValue() : 1;
                itemNbt = strOr(itemMap.get("nbt"), "");
            }
        } else if ("currency".equals(type)) {
            Object curObj = map.get("currency");
            if (curObj instanceof Map<?, ?> curMap) {
                currencyId = strOr(curMap.get("currency-id"), "money");
                currencyAmount = curMap.get("amount") != null ? ((Number) curMap.get("amount")).doubleValue() : 0;
            }
        } else if ("command".equals(type)) {
            Object cmdObj = map.get("commands");
            if (cmdObj instanceof List<?> cmdList) {
                commands = new ArrayList<>();
                for (Object c : cmdList) commands.add(String.valueOf(c));
            }
        } else if ("mail".equals(type)) {
            mailPresetId = strOr(map.get("mail-preset-id"), "");
        }

        return new SeasonRewardDefinition(id, 0, type, iconMaterial, iconName, iconAmount,
            itemSource, itemId, itemAmount, itemNbt, currencyId, currencyAmount, commands, mailPresetId);
    }

    private static String strOr(Object value, String defaultValue) {
        if (value == null) return defaultValue;
        String s = String.valueOf(value);
        return s.isBlank() ? defaultValue : s;
    }
}
