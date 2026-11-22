package xuanmo.arcartxsuite.api.capability;

import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tab 列表刷新能力接口。
 * <p>
 * 由 Tab 模块实现，供 Title、Chat 等模块在数据变更时触发 Tab 刷新。
 * <p>
 * 公开 capability：外部插件可经 {@code AxsCapabilities.get(TabRefreshable.class)} 获取。
 */
@PublicCapability
public interface TabRefreshable {

    /**
     * 在服务端线程申请隐身。结果在服务端线程完成；
     * 代理确认后才可对玩家启用隐身。
     */
    @NotNull CompletionStage<Boolean> requestVanish(@NotNull Player player);

    /**
     * 请求刷新指定玩家可见的 Tab。
     *
     * @param viewer 目标玩家
     * @param reason 触发原因（日志用）
     */
    void requestViewerRefresh(@NotNull Player viewer, @Nullable String reason);

    /**
     * 请求刷新所有在线玩家的 Tab。
     *
     * @param reason 触发原因（日志用）
     */
    void requestGlobalRefresh(@Nullable String reason);
}
