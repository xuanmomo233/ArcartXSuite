package xuanmo.arcartxsuite.abyss.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

/**
 * rewards.yml 解析：命名奖励集 + 随机权重 + 宝箱奖励。
 *
 * <pre>
 * rewards:
 *   basic_pack:
 *     items: ["material:DIAMOND:3"]
 *     mythic-items: ["BossSword:1"]
 *     money: 100            # 走宿主 currency（currency 字段选货币）
 *     currency: "money"
 *     exp: 50
 *     commands: ["say {player} 通关了！"]
 *   random_pack:
 *     type: random
 *     picks: 1              # 抽几次
 *     pool:
 *       - reward: basic_pack
 *         weight: 70
 *       - reward: rare_pack
 *         weight: 30
 *
 * chest-rewards:
 *   common_chest:
 *     title: "普通宝箱"
 *     rows: 3
 *     options:
 *       - item: "material:IRON_INGOT:5"
 *         weight: 50
 *       - item: "mythic:BossSword:1"
 *         weight: 10
 * </pre>
 */
public record RewardConfig(
    Map<String, RewardSet> rewards,
    Map<String, ChestReward> chestRewards
) {

    public record RewardEntry(String rewardId, int weight) { }

    public record RewardSet(
        String name,
        boolean random,
        int picks,
        List<RewardEntry> pool,
        List<String> items,
        List<String> mythicItems,
        List<String> neigeItems,
        List<String> overtureItems,
        List<String> mmoItems,
        double money,
        String currency,
        int exp,
        List<String> commands
    ) {
        static RewardSet load(String name, ConfigurationSection s) {
            List<RewardEntry> pool = new ArrayList<>();
            for (Map<?, ?> m : s.getMapList("pool")) {
                Object rid = m.get("reward");
                if (rid != null) {
                    int w = 1;
                    Object wo = m.get("weight");
                    if (wo instanceof Number n) w = n.intValue();
                    pool.add(new RewardEntry(String.valueOf(rid), w));
                }
            }
            return new RewardSet(
                name,
                "random".equalsIgnoreCase(s.getString("type", "")),
                s.getInt("picks", 1),
                pool,
                s.getStringList("items"),
                s.getStringList("mythic-items").isEmpty()
                    ? s.getStringList("mythic_items") : s.getStringList("mythic-items"),
                s.getStringList("neige-items").isEmpty()
                    ? s.getStringList("neige_items") : s.getStringList("neige-items"),
                s.getStringList("overture-items").isEmpty()
                    ? s.getStringList("overture_items") : s.getStringList("overture-items"),
                s.getStringList("mmo-items").isEmpty()
                    ? s.getStringList("mmo_items") : s.getStringList("mmo-items"),
                s.getDouble("money", 0.0),
                s.getString("currency", "money"),
                s.getInt("exp", 0),
                s.getStringList("commands")
            );
        }
    }

    public record ChestOption(String item, int weight) { }

    public record ChestReward(
        String name,
        String title,
        int rows,
        List<ChestOption> options
    ) {
        static ChestReward load(String name, ConfigurationSection s) {
            List<ChestOption> opts = new ArrayList<>();
            for (Map<?, ?> m : s.getMapList("options")) {
                Object it = m.get("item");
                if (it != null) {
                    int w = 1;
                    Object wo = m.get("weight");
                    if (wo instanceof Number n) w = n.intValue();
                    opts.add(new ChestOption(String.valueOf(it), w));
                }
            }
            return new ChestReward(
                name,
                s.getString("title", name),
                Math.min(6, Math.max(1, s.getInt("rows", 3))),
                opts
            );
        }
    }

    public static RewardConfig load(ConfigurationSection root) {
        Map<String, RewardSet> rewards = new LinkedHashMap<>();
        Map<String, ChestReward> chests = new LinkedHashMap<>();
        if (root == null) {
            return new RewardConfig(rewards, chests);
        }
        ConfigurationSection r = root.getConfigurationSection("rewards");
        if (r != null) {
            for (String key : r.getKeys(false)) {
                ConfigurationSection s = r.getConfigurationSection(key);
                if (s != null) rewards.put(key, RewardSet.load(key, s));
            }
        }
        // 兼容：根级直接写奖励名（无 rewards: 包裹）
        if (r == null) {
            for (String key : root.getKeys(false)) {
                if ("chest-rewards".equals(key) || "chest_rewards".equals(key)) continue;
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s != null) rewards.put(key, RewardSet.load(key, s));
            }
        }
        ConfigurationSection c = root.getConfigurationSection("chest-rewards");
        if (c == null) c = root.getConfigurationSection("chest_rewards");
        if (c != null) {
            for (String key : c.getKeys(false)) {
                ConfigurationSection s = c.getConfigurationSection(key);
                if (s != null) chests.put(key, ChestReward.load(key, s));
            }
        }
        return new RewardConfig(rewards, chests);
    }

    /** 单独的 chest_rewards.yml 文件加载入口。 */
    public static Map<String, ChestReward> loadChestOnly(ConfigurationSection root) {
        Map<String, ChestReward> chests = new LinkedHashMap<>();
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s != null) chests.put(key, ChestReward.load(key, s));
            }
        }
        return chests;
    }

    private static MemoryConfiguration toSection(Map<?, ?> m) {
        MemoryConfiguration mem = new MemoryConfiguration();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() != null) mem.set(String.valueOf(e.getKey()), e.getValue());
        }
        return mem;
    }
}
