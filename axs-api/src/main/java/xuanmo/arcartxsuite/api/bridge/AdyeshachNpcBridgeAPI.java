package xuanmo.arcartxsuite.api.bridge;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adyeshach NPC 桥接。
 * <p>
 * 查询附近 NPC、应用模型/动画、生成私有导航标记等。
 *
 * @since 1.2.0
 */
@ApiStability.Internal
public interface AdyeshachNpcBridgeAPI {

    /** 设置调试日志开关 */
    void setDebug(boolean debug);

    /** 初始化桥接 */
    boolean initialize();

    /** 关闭桥接 */
    void shutdown();

    /** 桥接是否可用 */
    boolean isAvailable();

    /**
     * 查找玩家附近的 NPC。
     * <p>
     * 扫描公共持久管理器与公共临时管理器（实例世界内由
     * {@link #spawnPublicTemporaryNpc} 创建的临时 NPC 也在结果内）。
     */
    @NotNull List<AdyeshachNearbyNpc> findNearby(@NotNull Player player, double range);

    /** 按显示名/ID 查找 NPC（公共持久 + 公共临时管理器） */
    @NotNull Optional<Object> findByName(@NotNull String name);

    /**
     * 按显示名/ID 定位 NPC 当前所在位置（动态导航目标解析用）。
     *
     * @param name NPC 显示名、customName 或实体 id（大小写不敏感精确匹配）
     * @return 实体当前位置；桥接不可用或找不到时返回空
     * @since 1.6.0
     */
    @NotNull Optional<Location> locateByName(@NotNull String name);

    /** 获取指定实体的所有可识别名称 */
    @NotNull List<String> getEntityNames(@Nullable Object adyeshachEntity);

    /** 注册可见事件处理器 */
    boolean registerVisibleHandler(@NotNull BiConsumer<Player, Object> handler);

    /** 注销可见事件处理器 */
    void unregisterVisibleHandler();

    /** 为玩家生成私有导航标记实体 */
    @Nullable Object spawnPrivateMarker(@NotNull Player player, @NotNull String markerId, @NotNull Location location);

    /** 传送玩家私有标记实体 */
    boolean teleportMarker(@NotNull Player player, @NotNull String markerId, @NotNull Location location);

    /** 移除玩家私有标记实体 */
    boolean removePrivateMarker(@NotNull Player player, @NotNull String markerId);

    /** 清理所有私有标记实体 */
    void clearAllPrivateMarkers();

    /** 获取玩家私有标记实体 */
    @Nullable Object getPrivateMarker(@NotNull Player player, @NotNull String markerId);

    /** 为 NPC 广播应用模型 */
    boolean applyModel(@Nullable Object adyeshachEntity, @Nullable String modelId, double scale);

    /** 为指定玩家对 NPC 应用模型 */
    boolean applyModelForPlayer(@Nullable Player player, @Nullable Object adyeshachEntity, @Nullable String modelId, double scale);

    /** 为 NPC 广播播放动画 */
    boolean applyAnimation(@Nullable Object adyeshachEntity, @Nullable String animation, double speed, int transitionTime, long keepTime);

    /** 为指定玩家对 NPC 播放动画 */
    boolean applyAnimationForPlayer(@Nullable Player player, @Nullable Object adyeshachEntity, @Nullable String animation, double speed, int transitionTime, long keepTime);

    /** 为 NPC 广播设置默认状态 */
    boolean applyDefaultState(@Nullable Object adyeshachEntity, @Nullable String state, @Nullable String animName);

    /** 为指定玩家对 NPC 设置默认状态 */
    boolean applyDefaultStateForPlayer(@Nullable Player player, @Nullable Object adyeshachEntity, @Nullable String state, @Nullable String animName);

    // ==================== 公共临时 NPC（实例化场景用） ====================

    /**
     * 在公共临时管理器中创建 NPC（不写入 Adyeshach 持久库，适合副本实例等临时场景）。
     * <p>
     * 实体对附近所有玩家可见，调用方负责 {@link #removeEntity} 清理。
     *
     * @param entityType  {@code EntityTypes} 枚举名（如 VILLAGER/WITCH/PLAYER），非法值降级 VILLAGER
     * @param location    生成位置（可为副本实例世界）
     * @param npcId       设定实体 id（Chemdah 会话按 {@code npc id: 'adyeshach <此值>'} 绑定），为空则保留 Adyeshach 分配值
     * @param displayName 头顶显示名（支持 & 色码），为空则不设置
     * @return 实体句柄（Adyeshach EntityInstance），失败返回 null
     */
    @Nullable Object spawnPublicTemporaryNpc(@NotNull String entityType, @NotNull Location location,
                                             @Nullable String npcId, @Nullable String displayName);

    /**
     * 删除 NPC/标记实体句柄（{@link #spawnPublicTemporaryNpc}/{@link #spawnPrivateMarker} 返回值）。
     *
     * @return 是否删除成功
     */
    boolean removeEntity(@Nullable Object adyeshachEntity);

    /**
     * 注册 NPC 右键交互处理器（全桥仅一个槽位，重复注册覆盖前者）。
     * <p>
     * handler 参数为（玩家, EntityInstance 句柄）。Adyeshach 实体为发包虚拟实体，
     * 不触发 Bukkit {@code PlayerInteractEntityEvent}，必须经此事件路由。
     *
     * @return 注册是否成功（Adyeshach 不可用时返回 false）
     */
    boolean registerInteractHandler(@NotNull BiConsumer<Player, Object> handler);

    /** 注销 NPC 交互处理器 */
    void unregisterInteractHandler();
}
