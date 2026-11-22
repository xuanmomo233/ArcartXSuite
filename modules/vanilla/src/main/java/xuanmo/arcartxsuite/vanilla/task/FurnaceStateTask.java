package xuanmo.arcartxsuite.vanilla.task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.vanilla.config.VanillaConfiguration;
import xuanmo.arcartxsuite.vanilla.recipe.WorkshopType;

/** Sends changes for the active native views, with one block read per station in each poll. */
public final class FurnaceStateTask {
    private final Plugin plugin;
    private final PacketBridgeAPI bridge;
    private final Map<WorkshopType, String> uiIds;
    private final VanillaConfiguration configuration;
    private final Map<UUID, Viewer> viewers = new HashMap<>();
    private BukkitTask task;
    private long ticks;

    public FurnaceStateTask(Plugin plugin, PacketBridgeAPI bridge, Map<WorkshopType, String> uiIds,
                            VanillaConfiguration configuration) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.uiIds = Map.copyOf(uiIds);
        this.configuration = configuration;
    }

    public void start() {
        stop();
        if (bridge == null || !bridge.isAvailable() || uiIds.isEmpty()) return;
        int interval = uiIds.keySet().stream().mapToInt(configuration::interval).min().orElse(2);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ticks += interval;
            tick();
        }, interval, interval);
        for (Player player : Bukkit.getOnlinePlayers()) {
            track(player, player.getOpenInventory());
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        ticks = 0;
        viewers.clear();
    }

    public void track(Player player, InventoryView view) {
        if (task == null) return;
        UUID id = player.getUniqueId();
        for (WorkshopType type : uiIds.keySet()) {
            if (!type.matches(view)) continue;
            Inventory inventory = view.getTopInventory();
            var location = inventory.getLocation();
            if (location != null && location.isWorldLoaded()) {
                Station station = new Station(location.getWorld(), location.getBlockX(), location.getBlockY(),
                    location.getBlockZ(), type);
                viewers.put(id, new Viewer(view, inventory, station));
                return;
            }
            break;
        }
        viewers.remove(id);
    }

    public void untrack(UUID id, InventoryView view) {
        Viewer viewer = viewers.get(id);
        if (viewer != null && viewer.view == view) viewers.remove(id);
    }

    private void tick() {
        if (viewers.isEmpty()) return;
        Map<Station, Sample> samples = new HashMap<>();
        for (var entry : List.copyOf(viewers.entrySet())) {
            UUID id = entry.getKey();
            Viewer viewer = entry.getValue();
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline() || player.getOpenInventory() != viewer.view) {
                viewers.remove(id, viewer);
                continue;
            }
            if (ticks < viewer.nextPoll) continue;
            WorkshopType type = viewer.station.type();
            viewer.nextPoll = ticks + configuration.interval(type);

            String uiId = uiIds.get(type);
            if (!bridge.isUiOpen(player, uiId)) {
                viewer.resetProgress();
                continue;
            }
            if (!samples.containsKey(viewer.station)) {
                samples.put(viewer.station, read(viewer.station));
            }
            Sample sample = samples.get(viewer.station);
            if (sample == null || !viewer.inventory.equals(sample.inventory())) {
                viewer.resetProgress();
                continue;
            }
            viewer.push(bridge, player, uiId, sample.progress());
        }
    }

    private static Sample read(Station station) {
        World world = station.world();
        if (!world.isChunkLoaded(station.x() >> 4, station.z() >> 4)) return null;
        var block = world.getBlockAt(station.x(), station.y(), station.z());
        Material expected = switch (station.type()) {
            case FURNACE -> Material.FURNACE;
            case BLAST_FURNACE -> Material.BLAST_FURNACE;
            case SMOKER -> Material.SMOKER;
            case BREWING -> Material.BREWING_STAND;
            default -> null;
        };
        if (expected == null || block.getType() != expected) return null;
        var state = block.getState();
        if (state instanceof Furnace furnace) {
            return new Sample(furnace.getInventory(),
                new FurnaceProgress(furnace.getBurnTime(), furnace.getCookTime(), Math.max(1, furnace.getCookTimeTotal())));
        }
        if (state instanceof BrewingStand stand) {
            return new Sample(stand.getInventory(), new BrewingProgress(stand.getBrewingTime(), stand.getFuelLevel()));
        }
        return null;
    }

    private record Station(World world, int x, int y, int z, WorkshopType type) {}
    private record Sample(Inventory inventory, Progress progress) {}

    private sealed interface Progress permits FurnaceProgress, BrewingProgress {
        String handler();
        Map<String, Object> payload(int burnTotal);
    }

    private record FurnaceProgress(int burn, int cook, int cookTotal) implements Progress {
        @Override public String handler() { return "furnace_state"; }
        @Override public Map<String, Object> payload(int burnTotal) {
            return Map.of("burnTime", burn, "burnTimeTotal", burnTotal, "cookTime", cook,
                "cookTimeTotal", cookTotal, "burning", burn > 0);
        }
    }

    private record BrewingProgress(int remaining, int fuel) implements Progress {
        @Override public String handler() { return "brewing_state"; }
        @Override public Map<String, Object> payload(int burnTotal) {
            return Map.of("remaining", remaining, "total", 400, "fuel", fuel);
        }
    }

    private static final class Viewer {
        final InventoryView view;
        final Inventory inventory;
        final Station station;
        long nextPoll;
        int previousBurn = -1;
        int burnTotal;
        Progress sent;
        int sentBurnTotal;

        Viewer(InventoryView view, Inventory inventory, Station station) {
            this.view = view;
            this.inventory = inventory;
            this.station = station;
        }

        void resetProgress() {
            previousBurn = -1;
            burnTotal = 0;
            sent = null;
        }

        void push(PacketBridgeAPI bridge, Player player, String uiId, Progress progress) {
            if (progress instanceof FurnaceProgress furnace) {
                int burn = furnace.burn();
                if (burn == 0 || burn > previousBurn) burnTotal = burn;
                previousBurn = burn;
            }
            int denominator = Math.max(1, burnTotal);
            if (progress.equals(sent) && denominator == sentBurnTotal) return;
            if (bridge.sendPacket(player, uiId, progress.handler(), progress.payload(denominator))) {
                sent = progress;
                sentBurnTotal = denominator;
            }
        }
    }
}
