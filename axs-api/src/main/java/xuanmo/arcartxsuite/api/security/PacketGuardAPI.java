package xuanmo.arcartxsuite.api.security;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端包频率限制 API。
 * <p>
 * 模块在处理客户端包时应调用 {@link #allow} 进行频率校验，
 * 防止客户端发送恶意高频包。
 */
public interface PacketGuardAPI {

    /**
     * Allows an operation according to the configured module/action rate rule.
     * Incoming packet handlers are guarded centrally by the framework route
     * layer and should not call this method for packet throttling.
     *
     * @param player       player identity
     * @param module       module key, such as {@code warehouse}
     * @param action       action key, such as {@code buy}
     * @param debugLogging whether to emit debug logging
     * @return true when the operation is allowed
     */
    boolean allow(Player player, String module, String action, boolean debugLogging);

    /**
     * 带 packetId 维度的限流校验（宿主路由层使用）。
     * <p>
     * 限流状态与 action 键上限按 {@code (module, packetId)} 命名空间隔离：
     * 某类包的高基数 action（如 tooltip 物品指纹）不会挤占同一模块下
     * 其他 packetId 的额度。默认实现委托给 {@link #allow(Player, String, String, boolean)}
     * 以保持向后兼容。
     *
     * @param player       player identity
     * @param module       module key
     * @param packetId     客户端包的 packetId（可为 null，视为无 packetId 维度）
     * @param action       action key
     * @param debugLogging whether to emit debug logging
     * @return true when the operation is allowed
     */
    default boolean allow(@NotNull Player player, @NotNull String module, @Nullable String packetId,
                          @Nullable String action, boolean debugLogging) {
        return allow(player, module, action, debugLogging);
    }

    /**
     * 诊断用：返回该玩家当前被跟踪的 action 键快照。
     * <p>
     * 返回 {@code "module|packetId" → action 键集合}（packetId 为空串表示
     * 无 packetId 维度的调用方）。可用于定位 action 键数超上限导致的静默丢包。
     *
     * @param playerId 玩家 UUID
     * @return 键桶快照；不支持诊断的实现返回空 Map
     */
    default @NotNull Map<String, ? extends Collection<String>> actionKeys(@NotNull UUID playerId) {
        return Map.of();
    }

    /**
     * 清除该玩家的全部限流状态（路由层在玩家退出时调用，让重进自愈）。
     *
     * @param playerId 玩家 UUID
     */
    default void clearPlayer(@NotNull UUID playerId) {
    }
}
