package xuanmo.arcartxsuite.tab.sync;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

final class TabRefreshQueue {

    private final LinkedHashSet<UUID> pendingViewerIds = new LinkedHashSet<>();

    private boolean globalRefreshRequested;
    private boolean flushScheduled;

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

    boolean requestGlobal() {
        globalRefreshRequested = true;
        pendingViewerIds.clear();
        if (!flushScheduled) {
            flushScheduled = true;
            return true;
        }
        return false;
    }

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

    void clear() {
        pendingViewerIds.clear();
        globalRefreshRequested = false;
        flushScheduled = false;
    }

    record DrainResult(boolean global, Set<UUID> viewerIds) {
    }
}
