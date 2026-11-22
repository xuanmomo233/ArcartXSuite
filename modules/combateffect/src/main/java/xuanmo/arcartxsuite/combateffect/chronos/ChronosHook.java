package xuanmo.arcartxsuite.combateffect.chronos;

import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.ChronosAccess;

/** Registers events using the same Chronos class loader used to probe its API. */
public final class ChronosHook {
    private ChronosHook() {}

    public static void listen(JavaPlugin plugin, ChronosAccess chronos, Listener owner,
                              String eventName, String idGetter, BiConsumer<Player, String> handler)
            throws ReflectiveOperationException {
        Class<? extends Event> type = chronos.eventType(eventName);
        Method playerGetter = type.getMethod("getPlayer");
        Method valueGetter = type.getMethod(idGetter);
        listen(plugin, chronos, owner, type, EventPriority.MONITOR, (listener, event) -> {
            try {
                handler.accept((Player) playerGetter.invoke(event), (String) valueGetter.invoke(event));
            } catch (ReflectiveOperationException e) {
                throw new EventException(e);
            }
        });
    }

    static void listen(JavaPlugin plugin, ChronosAccess chronos, Listener owner, Class<? extends Event> type,
                       EventPriority priority, EventExecutor executor) {
        Bukkit.getPluginManager().registerEvent(type, owner, priority, (listener, event) -> {
            if (chronos.enabled() && type.isInstance(event)) executor.execute(listener, event);
        }, plugin, true);
    }
}
