package xuanmo.arcartxsuite.conversation.service;

import java.util.List;

/**
 * 对话交互状态的工具类，提供回复选中项保持、索引环绕、滚动窗口与抑制时间计算等纯函数。
 * <p>
 * 属于 Conversation 模块内部组件，供 {@link ConversationService} 与
 * {@link ChemdahAxRenderer} 复用，无状态、不可实例化。
 */
final class ConversationInteractionStateSupport {

    private ConversationInteractionStateSupport() {
    }

    /**
     * 在有序回复 id 列表中保持或初始化选中项。
     * <p>
     * 优先沿用 {@code preferredId}；若无效则回退到列表首项。
     *
     * @param orderedIds  有序回复 id 列表
     * @param preferredId 期望保持的选中 id
     * @return 计算后的选中状态
     */
    static SelectionState preserveSelection(List<String> orderedIds, String preferredId) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return new SelectionState(-1, "");
        }

        if (preferredId != null && !preferredId.isBlank()) {
            int index = orderedIds.indexOf(preferredId);
            if (index >= 0) {
                return new SelectionState(index, preferredId);
            }
        }

        String selectedId = orderedIds.get(0);
        return new SelectionState(0, selectedId == null ? "" : selectedId);
    }

    /**
     * 在长度为 {@code size} 的列表中按偏移量环绕移动索引。
     *
     * @param currentIndex 当前索引
     * @param size         列表长度
     * @param delta        移动步数，可为负
     * @return 环绕后的目标索引；列表为空时返回 {@code -1}
     */
    static int wrapIndex(int currentIndex, int size, int delta) {
        if (size <= 0) {
            return -1;
        }
        int normalized = currentIndex;
        if (normalized < 0 || normalized >= size) {
            normalized = 0;
        }
        int next = (normalized + delta) % size;
        return next < 0 ? next + size : next;
    }

    /**
     * 计算抑制结束时间戳。
     *
     * @param now        当前时间戳
     * @param durationMs 抑制时长（毫秒），小于等于 0 表示不抑制
     * @return 抑制结束时间戳
     */
    static long computeSuppressUntil(long now, long durationMs) {
        return durationMs <= 0L ? now : now + durationMs;
    }

    /**
     * 判断当前时间是否处于抑制区间。
     *
     * @param now          当前时间戳
     * @param suppressUntil 抑制结束时间戳
     * @return {@code true} 表示仍处于抑制区间
     */
    static boolean isSuppressed(long now, long suppressUntil) {
        return now < suppressUntil;
    }

    /**
     * 计算可见窗口的起始索引，使选中项尽量居中显示。
     *
     * @param totalSize     总条目数
     * @param selectedIndex 当前选中索引
     * @param visibleSize   可见条目数
     * @return 窗口起始索引
     */
    static int windowStart(int totalSize, int selectedIndex, int visibleSize) {
        if (totalSize <= 0 || visibleSize <= 0 || totalSize <= visibleSize) {
            return 0;
        }
        int normalizedSelected = Math.max(0, Math.min(selectedIndex, totalSize - 1));
        int start = normalizedSelected - (visibleSize / 2);
        int maxStart = totalSize - visibleSize;
        if (start < 0) {
            return 0;
        }
        return Math.min(start, maxStart);
    }

    /**
     * 计算滚动比例（0.0 ~ 1.0），用于 UI 滚动条定位。
     *
     * @param totalSize     总条目数
     * @param selectedIndex 当前选中索引
     * @param visibleSize   可见条目数
     * @return 滚动比例，无需滚动时返回 {@code 0.0}
     */
    static double computeScrollRatio(int totalSize, int selectedIndex, int visibleSize) {
        if (totalSize <= 0 || visibleSize <= 0 || totalSize <= visibleSize) {
            return 0.0D;
        }
        int maxStart = totalSize - visibleSize;
        if (maxStart <= 0) {
            return 0.0D;
        }
        int start = windowStart(totalSize, selectedIndex, visibleSize);
        return Math.max(0.0D, Math.min(1.0D, (double) start / (double) maxStart));
    }

    static long distanceTenths(double distanceSquared) {
        return Math.round(Math.sqrt(Math.max(0.0D, distanceSquared)) * 10.0D);
    }

    static String distanceText(long tenths) {
        return (tenths / 10) + "." + (tenths % 10) + "m";
    }

    record SelectionState(int index, String selectedId) {
    }
}
