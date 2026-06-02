package xuanmo.arcartxsuite.tab.sync;

import org.bukkit.entity.Player;

public interface TabRefreshRequester {

    void requestViewerRefresh(Player viewer, String reason);

    void requestGlobalRefresh(String reason);
}
