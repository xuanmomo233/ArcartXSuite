package xuanmo.arcartxsuite.conversation.service;

import java.util.List;

final class ConversationInteractionStateSupport {

    private ConversationInteractionStateSupport() {
    }

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

    static long computeSuppressUntil(long now, long durationMs) {
        return durationMs <= 0L ? now : now + durationMs;
    }

    static boolean isSuppressed(long now, long suppressUntil) {
        return now < suppressUntil;
    }

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

    record SelectionState(int index, String selectedId) {
    }
}
