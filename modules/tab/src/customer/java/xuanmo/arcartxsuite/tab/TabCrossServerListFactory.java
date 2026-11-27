package xuanmo.arcartxsuite.tab;

import java.util.function.BooleanSupplier;
import org.bukkit.plugin.java.JavaPlugin;

final class TabCrossServerListFactory {
    static TabCrossServerList create(JavaPlugin plugin, BooleanSupplier allowed, TabPlayerList playerList) {
        return new TabVisibilityPublisher(plugin, allowed, playerList);
    }
}
