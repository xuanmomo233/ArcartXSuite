package xuanmo.arcartxsuite.lottery.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

/**
 * 单个奖池使用的积分定义及其产出规则。
 *
 * @param id 积分 ID，余额按该 ID 在所有奖池间共享
 * @param displayName 展示名称
 * @param icon 展示图标或图标 JSON
 * @param perPull 每次抽取固定产出的积分
 * @param rarityRewards 按稀有度产出的积分
 */
public record PointDefinition(
    @NotNull String id,
    @NotNull String displayName,
    @NotNull String icon,
    int perPull,
    @NotNull Map<String, Integer> rarityRewards
) {
    public static PointDefinition load(String id, ConfigurationSection section) {
        String displayName = section.getString("display-name", id);
        String icon = section.getString("icon", "");
        int perPull = section.getInt("per-pull", 0);
        Map<String, Integer> rarity = new LinkedHashMap<>();
        ConfigurationSection rewards = section.getConfigurationSection("rarity");
        if (rewards != null) {
            for (String key : rewards.getKeys(false)) {
                int amount = rewards.getInt(key, 0);
                if (amount > 0) rarity.put(key, amount);
            }
        }
        return new PointDefinition(id, displayName, icon, perPull,
            Collections.unmodifiableMap(rarity));
    }
}
