package xuanmo.arcartxsuite.vanilla.task;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.vanilla.config.VanillaConfiguration;
import xuanmo.arcartxsuite.vanilla.recipe.WorkshopType;

/** Polls active viewers of enabled cooking screens without loading chunks. */
public final class FurnaceStateTask {
    private final Plugin plugin;
    private final PacketBridgeAPI bridge;
    private final Map<WorkshopType, String> uiIds;
    private final VanillaConfiguration configuration;
    private final Map<UUID, WorkshopType> viewers = new HashMap<>();
    private final Map<UUID, Integer> maxBurn = new HashMap<>();
    private final Map<UUID, Integer> previousBurn = new HashMap<>();
    private final Map<UUID, Long> lastPush = new HashMap<>();
    private BukkitTask task;
    private long ticks;
    public FurnaceStateTask(Plugin plugin, PacketBridgeAPI bridge, Map<WorkshopType, String> uiIds, VanillaConfiguration configuration) {
        this.plugin = plugin; this.bridge = bridge; this.uiIds = Map.copyOf(uiIds); this.configuration = configuration;
    }
    public void start() {
        stop();
        int interval = uiIds.keySet().stream().mapToInt(configuration::interval).min().orElse(2);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> { ticks += interval; tick(); }, interval, interval);
    }
    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        viewers.clear(); maxBurn.clear(); previousBurn.clear(); lastPush.clear();
    }
    public void track(Player player, WorkshopType type) {
        if (uiIds.containsKey(type)) { viewers.put(player.getUniqueId(), type); lastPush.remove(player.getUniqueId()); }
    }
    public void untrackPlayer(UUID id) { viewers.remove(id); maxBurn.remove(id); previousBurn.remove(id); lastPush.remove(id); }
    private void tick() {
        for (UUID id : List.copyOf(viewers.keySet())) {
            Player player = Bukkit.getPlayer(id);
            WorkshopType type = viewers.get(id);
            if (player == null || !player.isOnline() || !type.matches(player.getOpenInventory())) { untrackPlayer(id); continue; }
            if (ticks - lastPush.getOrDefault(id, -100L) < configuration.interval(type)) continue;
            lastPush.put(id, ticks);
            var location = player.getOpenInventory().getTopInventory().getLocation();
            if (location == null || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) continue;
            if(type==WorkshopType.BREWING){
                if(location.getBlock().getState() instanceof org.bukkit.block.BrewingStand stand){
                    bridge.sendPacket(player,uiIds.get(type),"brewing_state",Map.of("remaining",stand.getBrewingTime(),"total",400,"fuel",stand.getFuelLevel()));
                }
                continue;
            }
            if (!(location.getBlock().getState() instanceof Furnace furnace)) continue;
            int burn = furnace.getBurnTime();
            int total = burn == 0 ? 0 : (burn > previousBurn.getOrDefault(id, -1) ? burn : maxBurn.getOrDefault(id, burn));
            previousBurn.put(id, burn);
            maxBurn.put(id, total);
            bridge.sendPacket(player, uiIds.get(type), "furnace_state", Map.of(
                "burnTime", burn, "burnTimeTotal", Math.max(1, total),
                "cookTime", furnace.getCookTime(), "cookTimeTotal", Math.max(1, furnace.getCookTimeTotal()), "burning", burn > 0));
        }
    }
}
