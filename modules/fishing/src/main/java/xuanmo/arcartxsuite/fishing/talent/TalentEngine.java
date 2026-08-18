package xuanmo.arcartxsuite.fishing.talent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.fishing.model.FishingSeasonData;

/**
 * 天赋树引擎，负责天赋定义加载、点亮校验、联动解锁与效果聚合计算。
 * <p>
 * 关联模块：{@code fishing}。从 {@code talents.yml} 加载天赋树定义，
 * 提供 {@link #canUnlock}、{@link #unlock}、{@link #computeBonuses} 等核心方法。
 */
public final class TalentEngine {

    private final Map<String, TalentNode> nodesById = new HashMap<>();
    private final Map<String, String> treeDisplayNames = new HashMap<>();
    private final Map<String, String> treeIcons = new HashMap<>();
    private final Map<String, TalentTreeDefinition> treesById = new HashMap<>();
    private final java.util.List<TalentNode> linkNodes = new java.util.ArrayList<>();

    public TalentEngine(@NotNull Map<String, TalentTreeDefinition> trees,
                        @NotNull java.util.List<TalentNode> links) {
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
     * @return 图标物品 ID，不存在返回 "minecraft:stone"
     */
    public @NotNull String treeIcon(@NotNull String treeId) {
        return treeIcons.getOrDefault(treeId, "minecraft:stone");
    }

    /**
     * 判断玩家是否可以点亮指定节点。
     * <p>
     * 条件：
     * <ol>
     *   <li>节点存在</li>
     *   <li>玩家有未分配天赋点</li>
     *   <li>节点尚未点亮</li>
     *   <li>所有前置节点均已点亮</li>
     * </ol>
     *
     * @param data   玩家赛季数据
     * @param nodeId 节点 ID
     * @return {@code true} 表示可以点亮
     */
    public boolean canUnlock(@NotNull FishingSeasonData data, @NotNull String nodeId) {
        TalentNode node = nodesById.get(nodeId);
        if (node == null) return false;
        if (data.unspentTalentPoints() <= 0) return false;
        if (data.talents().contains(nodeId)) return false;
        // 检查前置
        for (String prereq : node.prerequisites()) {
            if (!data.talents().contains(prereq)) return false;
        }
        return true;
    }

    /**
     * 点亮一个天赋节点（不保存到数据库，调用方负责持久化）。
     *
     * @param data   玩家赛季数据
     * @param nodeId 节点 ID
     * @return 更新后的赛季数据，无法点亮时返回原数据
     */
    public @NotNull FishingSeasonData unlock(@NotNull FishingSeasonData data, @NotNull String nodeId) {
        if (!canUnlock(data, nodeId)) return data;
        return data.withTalentUnlocked(nodeId);
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
     * 相同效果类型的效果值相加，最终返回效果类型 → 总加成值的映射。
     *
     * @param data 玩家赛季数据
     * @return 效果加成映射
     */
    public @NotNull Map<String, Double> computeBonuses(@NotNull FishingSeasonData data) {
        Map<String, Double> bonuses = new HashMap<>();
        for (String nodeId : data.talents()) {
            TalentNode node = nodesById.get(nodeId);
            if (node == null) continue;
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
