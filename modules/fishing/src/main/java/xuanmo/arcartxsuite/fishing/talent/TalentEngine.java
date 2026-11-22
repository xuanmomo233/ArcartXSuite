package xuanmo.arcartxsuite.fishing.talent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.fishing.model.FishingSeasonData;

/**
 * 天赋树引擎，负责天赋定义加载、升级校验、联动解锁与效果聚合计算。
 * <p>
 * 关联模块：{@code fishing}。从 {@code talents.yml} 加载天赋树定义，
 * 提供 {@link #canLevelUp}、{@link #levelUp}、{@link #computeBonuses} 等核心方法。
 * <p>
 * 天赋节点支持多级升级：每次升级消耗 1 点天赋点，节点等级 +1，直到达到 maxLevel。
 * 联动节点固定 1 级，前置条件满足后一次性解锁。
 */
public final class TalentEngine {

    private final Map<String, TalentNode> nodesById = new HashMap<>();
    private final Map<String, String> treeDisplayNames = new HashMap<>();
    private final Map<String, IconDefinition> treeIcons = new HashMap<>();
    private final Map<String, TalentTreeDefinition> treesById = new HashMap<>();
    private final java.util.List<TalentNode> linkNodes = new java.util.ArrayList<>();
    private final Map<String, Integer> prerequisiteLevels;

    public TalentEngine(@NotNull Map<String, TalentTreeDefinition> trees,
                        @NotNull java.util.List<TalentNode> links) {
        this(trees, links, Map.of("2", 1, "3", 1, "4", 1, "5", 1, "6", 1, "7", 1, "link", 1));
    }

    public TalentEngine(@NotNull Map<String, TalentTreeDefinition> trees,
                        @NotNull java.util.List<TalentNode> links,
                        @NotNull Map<String, Integer> prerequisiteLevels) {
        this.prerequisiteLevels = new HashMap<>(prerequisiteLevels);
        // 注册树节点
        for (Map.Entry<String, TalentTreeDefinition> entry : trees.entrySet()) {
            String treeId = entry.getKey();
            TalentTreeDefinition tree = entry.getValue();
            treeDisplayNames.put(treeId, tree.displayName());
            treeIcons.put(treeId, tree.icon());
            treesById.put(treeId, tree);
            for (TalentNode node : tree.nodes()) {
                nodesById.put(node.id(), node);
            }
        }
        // 注册联动节点（treeId 为空）
        for (TalentNode link : links) {
            nodesById.put(link.id(), link);
            linkNodes.add(link);
        }
    }

    /**
     * 获取所有天赋树定义。
     *
     * @return 树 ID → 树定义的映射
     */
    public @NotNull Map<String, TalentTreeDefinition> getTrees() {
        return java.util.Collections.unmodifiableMap(treesById);
    }

    /**
     * 获取所有联动节点。
     *
     * @return 联动节点列表
     */
    public @NotNull java.util.List<TalentNode> getLinkNodes() {
        return java.util.Collections.unmodifiableList(linkNodes);
    }

    /**
     * 获取前置等级要求映射（目标 tier/link → 前置节点最低等级）。
     *
     * @return 不可变的前置等级映射
     */
    public @NotNull Map<String, Integer> prerequisiteLevels() {
        return java.util.Collections.unmodifiableMap(prerequisiteLevels);
    }

    /**
     * 获取指定节点的前置等级要求。
     * <p>
     * 普通节点按 tier 查找（key = tier 数字字符串），联动节点按 "link" 查找。
     * 未配置时默认 1（只需前置节点点亮即可）。
     *
     * @param node 目标节点
     * @return 前置节点需达到的最低等级
     */
    public int prerequisiteLevelFor(@NotNull TalentNode node) {
        String key = node.isLink() ? "link" : String.valueOf(node.tier());
        return prerequisiteLevels.getOrDefault(key, 1);
    }

    /**
     * 获取节点定义。
     *
     * @param nodeId 节点 ID
     * @return 节点定义，不存在返回 null
     */
    public @Nullable TalentNode getNode(@NotNull String nodeId) {
        return nodesById.get(nodeId);
    }

    /**
     * 获取所有已注册的节点 ID。
     *
     * @return 节点 ID 集合
     */
    public @NotNull Set<String> allNodeIds() {
        return new HashSet<>(nodesById.keySet());
    }

    /**
     * 获取树显示名称。
     *
     * @param treeId 树 ID
     * @return 显示名称，不存在返回 treeId
     */
    public @NotNull String treeDisplayName(@NotNull String treeId) {
        return treeDisplayNames.getOrDefault(treeId, treeId);
    }

    /**
     * 获取树图标。
     *
     * @param treeId 树 ID
     * @return 图标定义，不存在返回 minecraft:stone 的 IconDefinition
     */
    public @NotNull IconDefinition treeIcon(@NotNull String treeId) {
        return treeIcons.getOrDefault(treeId, IconDefinition.load("minecraft:stone"));
    }

    /**
     * 判断玩家是否可以解锁指定节点。
     * <p>
     * 新版条件：
     * <ol>
     *   <li>节点存在</li>
     *   <li>玩家有未分配天赋点</li>
     *   <li>节点未点亮（当前等级为 0）</li>
     *   <li>所有前置节点已点亮（等级 ≥ 1）</li>
     * </ol>
     *
     * @param data   玩家赛季数据
     * @param nodeId 节点 ID
     * @return {@code true} 表示可以解锁
     */
    public boolean canLevelUp(@NotNull FishingSeasonData data, @NotNull String nodeId) {
        TalentNode node = nodesById.get(nodeId);
        if (node == null) return false;
        if (data.unspentTalentPoints() <= 0) return false;
        int currentLevel = data.talents().getOrDefault(nodeId, 0);
        if (currentLevel >= node.maxLevel()) return false;
        // 未点亮时检查前置节点是否已点亮（≥ 1）
        if (currentLevel == 0) {
            for (String prereq : node.prerequisites()) {
                if (data.talents().getOrDefault(prereq, 0) < 1) return false;
            }
        }
        return true;
    }

    /**
     * 解锁或升级一个天赋节点，消耗 1 点天赋点。
     *
     * @param data   玩家赛季数据
     * @param nodeId 节点 ID
     * @return 更新后的赛季数据，无法解锁时返回原数据
     */
    public @NotNull FishingSeasonData levelUp(@NotNull FishingSeasonData data, @NotNull String nodeId) {
        if (!canLevelUp(data, nodeId)) return data;
        return data.withTalentLeveledUp(nodeId, nodesById.get(nodeId).maxLevel());
    }

    /**
     * 重置玩家天赋树（退还所有已分配天赋点）。
     *
     * @param data 玩家赛季数据
     * @return 更新后的赛季数据
     */
    public @NotNull FishingSeasonData reset(@NotNull FishingSeasonData data) {
        return data.withTalentsReset();
    }

    /**
     * 聚合计算玩家所有已点亮天赋的效果加成。
     * <p>
     * 根据每个节点的当前等级取对应的 levelValue，相同效果类型的值相加。
     * 联动节点的多效果（effects 列表）也一并聚合。
     *
     * @param data 玩家赛季数据
     * @return 效果加成映射（效果类型 → 总加成值）
     */
    public @NotNull Map<String, Double> computeBonuses(@NotNull FishingSeasonData data) {
        Map<String, Double> bonuses = new HashMap<>();
        for (Map.Entry<String, Integer> entry : data.talents().entrySet()) {
            String nodeId = entry.getKey();
            int level = entry.getValue();
            if (level <= 0) continue;
            TalentNode node = nodesById.get(nodeId);
            if (node == null) continue;

            // 单效果节点：取当前等级对应的 levelValue
            if (node.effectType() != null && !node.levelValues().isEmpty()) {
                double value = node.valueAtLevel(level);
                bonuses.merge(node.effectType(), value, Double::sum);
            }

            // 多效果节点（联动 link-3）：固定效果值（1 级）
            for (TalentEffect effect : node.effects()) {
                bonuses.merge(effect.effectType(), effect.effectValue(), Double::sum);
            }
        }
        return bonuses;
    }

    /**
     * 获取指定效果类型的总加成值。
     *
     * @param bonuses    效果加成映射（来自 {@link #computeBonuses}）
     * @param effectType 效果类型
     * @return 总加成值，无该效果返回 0
     */
    public static double getBonus(@NotNull Map<String, Double> bonuses, @NotNull String effectType) {
        return bonuses.getOrDefault(effectType, 0.0);
    }
}
