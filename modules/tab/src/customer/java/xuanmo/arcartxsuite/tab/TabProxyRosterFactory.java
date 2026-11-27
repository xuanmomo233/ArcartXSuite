package xuanmo.arcartxsuite.tab;

import java.util.function.BooleanSupplier;
import org.bukkit.plugin.java.JavaPlugin;

final class TabProxyRosterFactory {
    static TabProxyRoster create(JavaPlugin plugin, BooleanSupplier allowed) {
        return new TabVisibilityPublisher(plugin, allowed);
    }
}
