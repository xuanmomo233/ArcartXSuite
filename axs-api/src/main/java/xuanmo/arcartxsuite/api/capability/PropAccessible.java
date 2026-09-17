package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 快捷道具公开能力接口（公开 capability）。
 * <p>
 * 由 Prop 模块实现，供外部插件查询已注册道具并触发道具应用。
 * 道具使用可订阅 EventBus 主题 {@code axs.prop.prop_used}。
 * <p>
 * 外部插件经 {@code AxsCapabilities.get(PropAccessible.class)} 获取；
 * 所有方法请在 Bukkit 主线程调用。
 */
@PublicCapability
public interface PropAccessible {

    /** 已注册的道具 id 列表。 */
    @NotNull List<String> propIds();

    /**
     * 将指定道具效果应用到玩家主手物品上。
     *
     * @param player 目标玩家
     * @param propId 道具 id
     * @return 应用结果（成功/失败原因）
     */
    @NotNull PropApplyResult applyPropToMainHand(@NotNull Player player, @NotNull String propId);

    /**
     * 道具应用结果。
     */
    record PropApplyResult(boolean success, @NotNull String message) {
    }
}
