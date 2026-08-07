package xuanmo.arcartxsuite.tab.sync;

import org.bukkit.entity.Player;

/**
 * Tab 刷新请求接口。
 * <p>
 * 定义发起单玩家刷新与全局刷新的契约，由 Tab 模块内部实现。
 * 关联模块：{@code tab}。
 */
public interface TabRefreshRequester {

    /**
     * 请求刷新指定玩家的 Tab 列表。
     *
     * @param viewer 目标观众玩家
     * @param reason 刷新原因，用于调试日志
     */
    void requestViewerRefresh(Player viewer, String reason);

    /**
     * 请求全局刷新所有玩家的 Tab 列表。
     *
     * @param reason 刷新原因，用于调试日志
     */
    void requestGlobalRefresh(String reason);
}
