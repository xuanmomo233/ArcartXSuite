package xuanmo.arcartxsuite.fishing.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.scheduler.ModuleCallback;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.MapExternalTargetSpec;
import xuanmo.arcartxsuite.api.capability.MapNavigable;
import xuanmo.arcartxsuite.fishing.model.WaterArea;
import xuanmo.arcartxsuite.fishing.model.WaterArea.School;

/** Local water activity and per-player discovered map targets. Main thread only. */
final class FishSchoolTracker {
    private static final String MAP_SOURCE = "fishing";
    private static final double DISCOVERY_RANGE = 24 * 24;
    private static final Particle WAKE = wakeParticle();
    private final Map<UUID, List<MapExternalTargetSpec>> discovered = new HashMap<>();
    private final Supplier<MapNavigable> mapProvider;
    private MapNavigable lastMap;
    private BukkitTask task;
    private Supplier<List<WaterArea>> waters = List::of;
    private final ModuleCallback pulse = new ModuleCallback(() -> pulse(this.waters.get()));

    FishSchoolTracker(Supplier<MapNavigable> mapProvider) {
        this.mapProvider = mapProvider;
    }

    void start(Plugin plugin, Supplier<List<WaterArea>> waters) {
        this.waters=waters;
        task = Bukkit.getScheduler().runTaskTimer(plugin, pulse, 1, 20);
    }

    void shutdown() {
        pulse.close();
        if (task != null) task.cancel();
        MapNavigable map = mapProvider.get();
        if (map != null) {
            for (Player player : Bukkit.getOnlinePlayers()) map.clearExternalTargets(player, MAP_SOURCE, true);
        }
        discovered.clear();
        lastMap = null;
    }

    java.util.concurrent.CompletionStage<Void> stopped() { return pulse.stopped(); }

    void forget(Player player) {
        discovered.remove(player.getUniqueId());
        MapNavigable map = mapProvider.get();
        if (map != null) map.clearExternalTargets(player, MAP_SOURCE, true);
    }

    void refreshMap(Player player) {
        MapNavigable map = mapProvider.get();
        if (map != null) map.reconcileExternalTargets(player, MAP_SOURCE,
            discovered.getOrDefault(player.getUniqueId(), List.of()));
    }

    @Nullable School atHook(WaterArea area, Location hook) {
        World world = hook.getWorld();
        if (world == null) return null;
        for (School school : area.schools()) {
            double dx = hook.getX() - school.x();
            double dz = hook.getZ() - school.z();
            if (dx * dx + dz * dz <= school.radius() * school.radius()
                    && Math.abs(hook.getY() - school.y()) <= 2 && isActive(area, school, world)) return school;
        }
        return null;
    }

    private void pulse(List<WaterArea> waters) {
        MapNavigable map = mapProvider.get();
        boolean mapChanged = map != lastMap;
        lastMap = map;
        Map<World, List<ActiveSchool>> byWorld = new HashMap<>();
        for (WaterArea area : waters) {
            if (area.worldName() == null || area.schools().isEmpty()) continue;
            World world = Bukkit.getWorld(area.worldName());
            if (world == null) continue;
            for (School school : area.schools()) {
                if (isActive(area, school, world)) {
                    byWorld.computeIfAbsent(world, ignored -> new ArrayList<>()).add(new ActiveSchool(area, school));
                }
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            Location playerLocation = player.getLocation();
            List<MapExternalTargetSpec> old = discovered.getOrDefault(player.getUniqueId(), List.of());
            List<MapExternalTargetSpec> targets = new ArrayList<>();
            for (ActiveSchool active : byWorld.getOrDefault(world, List.of())) {
                School school = active.school();
                Location center = new Location(world, school.x(), school.y(), school.z());
                boolean nearby = playerLocation.distanceSquared(center) <= DISCOVERY_RANGE;
                if (nearby) player.spawnParticle(WAKE, center, 9, 0.65, 0.05, 0.65, 0.015);
                if (nearby || old.stream().anyMatch(target -> target.targetId().equals(school.id())
                        && target.worldId().equals(world.getName()))) {
                    targets.add(new MapExternalTargetSpec(school.id(), MAP_SOURCE, world.getName(),
                        active.area().displayName() + " · 鱼讯", "浮漂落在水纹附近时更容易遇到该鱼群",
                        school.shoreX(), school.shoreY(), school.shoreZ(), null, false, 0, true));
                }
            }
            if (targets.isEmpty()) discovered.remove(player.getUniqueId());
            else discovered.put(player.getUniqueId(), List.copyOf(targets));
            if (map != null && (mapChanged || !old.equals(targets))) {
                map.reconcileExternalTargets(player, MAP_SOURCE, targets);
            }
        }
    }

    private record ActiveSchool(WaterArea area, School school) {}

    private static boolean isActive(WaterArea area, School school, World world) {
        if (!area.enabled() || !world.getName().equalsIgnoreCase(area.worldName())
                || (school.rainOnly() && !world.hasStorm())
                || Math.floorMod(world.getFullTime(), school.intervalTicks()) >= school.activeTicks()) return false;
        Location center = new Location(world, school.x(), school.y(), school.z());
        int x = center.getBlockX();
        int z = center.getBlockZ();
        int waterY = (int) Math.floor(school.y() - 0.1);
        return area.contains(center) && world.isChunkLoaded(x >> 4, z >> 4)
            && waterY >= world.getMinHeight() && waterY + 1 < world.getMaxHeight()
            && world.getBlockAt(x, waterY, z).getType() == Material.WATER
            && world.getBlockAt(x, waterY + 1, z).getType().isAir();
    }

    private static Particle wakeParticle() {
        try {
            return Particle.valueOf("FISHING");
        } catch (IllegalArgumentException ignored) {
            return Particle.valueOf("WATER_WAKE");
        }
    }
}
