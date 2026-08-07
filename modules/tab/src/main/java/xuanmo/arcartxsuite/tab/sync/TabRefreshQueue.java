package xuanmo.arcartxsuite.tab.sync;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tab 刷新请求队列。
 * <p>
 * 合并并去重单玩家刷新请求与全局刷新请求，避免同一 tick 内重复刷新。
 * 当全局刷新被请求时，所有单玩家请求会被吸收。
 * 关联模块：{@code tab}。
 */
final class TabRefreshQueue {

    private final LinkedHashSet<UUID> pendingViewerIds = new LinkedHashSet<>();

    private boolean globalRefreshRequested;
    private boolean flushScheduled;

    /**
     * 请求刷新指定玩家。
     * <p>
     * 若已有全局刷新待处理则忽略单玩家请求。
     *
     * @param viewerId 观众玩家唯一标识
     * @return {@code true} 表示本次请求触发了新的刷新调度；{@code false} 表示已有调度在排队
     */
    boolean requestViewer(UUID viewerId) {
        if (viewerId == null) {
            return false;
        }
        if (!globalRefreshRequested) {
            pendingViewerIds.add(viewerId);
        }
        if (!flushScheduled) {
            flushScheduled = true;
            return true;
        }
        return false;
    }

    /**
     * 请求全局刷新，吸收所有单玩家请求。
     *
     * @return {@code true} 表示本次请求触发了新的刷新调度；{@code false} 表示已有调度在排队
     */
    boolean requestGlobal() {
        globalRefreshRequested = true;
        pendingViewerIds.clear();
        if (!flushScheduled) {
            flushScheduled = true;
            return true;
        }
        return false;
    }

    /**
     * 排空队列并返回待刷新结果。
     *
     * @return 排空结果，包含是否为全局刷新及待刷新的玩家集合
     */
    DrainResult drain() {
        flushScheduled = false;
        if (globalRefreshRequested) {
            globalRefreshRequested = false;
            pendingViewerIds.clear();
            return new DrainResult(true, Set.of());
        }
        if (pendingViewerIds.isEmpty()) {
            return new DrainResult(false, Set.of());
        }
        Set<UUID> viewerIds = Set.copyOf(pendingViewerIds);
        pendingViewerIds.clear();
        return new DrainResult(false, viewerIds);
    }

    /**
     * 清空队列，重置所有状态。
     */
    void clear() {
        pendingViewerIds.clear();
        globalRefreshRequested = false;
        flushScheduled = false;
    }

    /**
     * 排空结果。
     *
     * @param global    是否为全局刷新
     * @param viewerIds 待刷新的玩家唯一标识集合（全局刷新时为空集）
     */
    record DrainResult(boolean global, Set<UUID> viewerIds) {
    }
}
