package xuanmo.arcartxsuite.tab;

import org.bukkit.event.Listener;
import xuanmo.arcartxsuite.api.capability.TabRefreshable;

interface TabProxyRoster extends Listener, TabRefreshable, AutoCloseable {
    @Override
    void close();
}
