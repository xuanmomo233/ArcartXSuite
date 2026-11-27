package xuanmo.arcartxsuite.tab;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import xuanmo.arcartxsuite.api.capability.TabRefreshable;

interface TabCrossServerList extends Listener, TabRefreshable, AutoCloseable {
    /** Remote entries actually injected for this viewer's current connection. */
    List<TabPlayerList.RemotePlayer> remotePlayers(Player viewer);

    @Override
    void close();
}
