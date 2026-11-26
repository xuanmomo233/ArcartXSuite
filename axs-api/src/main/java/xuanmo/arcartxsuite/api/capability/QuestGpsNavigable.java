package xuanmo.arcartxsuite.api.capability;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 任务导航能力接口（公开 capability）。
 * <p>
 * 由 QuestGPS 模块实现，供 EventPacket 等模块跨模块调用；
 * 外部 Bukkit 插件经 {@code AxsCapabilities.get(QuestGpsNavigable.class)} 获取。
 */
@PublicCapability
public interface QuestGpsNavigable {

    /** 任务状态（Chemdah 档案视角：未接/进行中/已完成） */
    enum QuestState {
        /** 未接取（或档案未加载） */
        NONE,
        /** 已接取进行中 */
        ACTIVE,
        /** 已完成 */
        COMPLETED
    }

    /** 向玩家推送任务供选择 */
    void offerQuest(@NotNull Player player, @NotNull String questId, boolean openMenu);

    /** 尝试向玩家推荐可用任务。返回 true 表示已触发推荐；旧实现未提供确认能力时返回 false。 */
    default boolean tryOfferQuest(@NotNull Player player, @NotNull String questId, boolean openMenu) {
        return false;
    }

    /** 让玩家接受任务 */
    void acceptQuest(@NotNull Player player, @NotNull String questId);

    /** 打开任务菜单 */
    void openMenu(@NotNull Player player);

    /** 追踪任务 */
    void trackQuest(@NotNull Player player, @NotNull String questId);

    /** 追踪任务的指定步骤 */
    void trackTask(@NotNull Player player, @NotNull String questId, @NotNull String taskId);

    /** 检查事件规则是否被主线门禁锁定 */
    boolean eventRuleLocked(@NotNull Player player, @NotNull String ruleId);

    /** 检查其他模块入口是否被主线门禁锁定 */
    boolean moduleEntryLocked(@NotNull Player player, @NotNull String moduleEntryId);

    /**
     * 查询玩家某任务的 Chemdah 档案状态。
     * <p>
     * questId 与 {@link #acceptQuest} 同语义（questgps 配置/别名可解析的 id）。
     * 档案未加载或从未接取返回 {@link QuestState#NONE}。
     */
    @NotNull QuestState questState(@NotNull Player player, @NotNull String questId);
}
