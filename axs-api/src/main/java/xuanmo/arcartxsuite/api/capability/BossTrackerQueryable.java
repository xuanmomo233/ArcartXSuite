package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Boss 追踪查询能力接口（公开 capability）。
 * <p>
 * 由 EntityTracker 模块实现，供外部插件查询当前活跃 Boss 会话。
 * Boss 击杀可订阅 EventBus 主题 {@code axs.entitytracker.boss_kill}。
 * <p>
 * 外部插件经 {@code AxsCapabilities.get(BossTrackerQueryable.class)} 获取。
 */
@PublicCapability
public interface BossTrackerQueryable {

    /**
     * 查询实体是否为被追踪的配置 Boss。
     */
    boolean isTrackedBoss(@NotNull UUID entityUuid);

    /** 当前活跃 Boss 会话数。 */
    int activeSessionCount();

    /** 正在观看 Boss 面板的玩家总数。 */
    int activeViewerCount();

    /**
     * 当前活跃 Boss 会话列表。
     *
     * @param mobIdFilter MythicMob id 过滤，null 表示不过滤
     */
    @NotNull List<BossSessionInfo> activeSessions(@Nullable String mobIdFilter);

    /**
     * 活跃 Boss 会话只读视图。
     */
    record BossSessionInfo(
        @NotNull UUID entityUuid,
        @NotNull String mythicMobId,
        @NotNull String displayName,
        double health,
        double maxHealth,
        int participantCount,
        int trackedPlayerCount,
        double totalDamage
    ) {
    }
}
