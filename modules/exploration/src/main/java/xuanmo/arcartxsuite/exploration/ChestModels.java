package xuanmo.arcartxsuite.exploration;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI;

/** Each live chest is a temporary Adyeshach NPC in its owner's private entity manager. */
final class ChestModels {
    private static final double REACH_SQUARED = 16.0;
    private final AdyeshachNpcBridgeAPI bridge;
    private final Plugin plugin;
    private final Map<UUID, Chest> chests = new ConcurrentHashMap<>();
    private boolean listening;
    private boolean closed;

    private record Chest(Player owner, String marker, String entityId, Location position,
                         Consumer<Player> onClick, AtomicBoolean clickQueued) {}

    ChestModels(AdyeshachNpcBridgeAPI bridge, Plugin plugin) {
        this.bridge = bridge;
        this.plugin = plugin;
    }

    boolean spawn(Player owner, String modelId, double scale, Consumer<Player> onClick) {
        if (closed || bridge == null || !owner.isOnline() || chests.containsKey(owner.getUniqueId())) return false;
        String marker = "exploration_" + UUID.randomUUID();
        boolean spawned = false;
        boolean retained = false;
        try {
            if (!listening) {
                if (!bridge.initialize() || !bridge.registerInteractHandler(this::interact)) return false;
                listening = true;
            }
            Location position = findPosition(owner);
            if (position == null) return false;
            Object handle = bridge.spawnPrivateMarker(owner, marker, position);
            if (handle == null) return false;
            spawned = true;
            String entityId = Ady.configure(handle, modelId, scale);
            if (entityId == null || !bridge.applyModelForPlayer(owner, handle, modelId, scale)) return false;
            chests.put(owner.getUniqueId(), new Chest(owner, marker, entityId, position, onClick, new AtomicBoolean()));
            retained = true;
            return true;
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().log(Level.WARNING, "无法生成探索宝箱模型 " + modelId, failure);
            return false;
        } finally {
            if (spawned && !retained) bridge.removePrivateMarker(owner, marker);
        }
    }

    private static Location findPosition(Player owner) {
        Location feet = owner.getLocation();
        World world = owner.getWorld();
        // Prefer the ground ahead. If a wall or a ledge blocks it, try beside and behind the player.
        for (int turn : new int[]{0, 45, -45, 90, -90, 135, -135, 180}) {
            double yaw = Math.toRadians(feet.getYaw() + turn);
            Location from = feet.clone().add(-Math.sin(yaw) * 1.6, 1.25, Math.cos(yaw) * 1.6);
            if (!from.getBlock().isPassable()) continue;
            var ground = world.rayTraceBlocks(from, new Vector(0, -1, 0), 2.6, FluidCollisionMode.NEVER, true);
            if (ground == null || ground.getHitBlockFace() != BlockFace.UP) continue;
            Location position = ground.getHitPosition().toLocation(world).add(0, 0.01, 0);
            Location center = position.clone().add(0, 0.45, 0);
            Location eye = owner.getEyeLocation();
            Vector direction = center.toVector().subtract(eye.toVector());
            if (world.rayTraceBlocks(eye, direction, direction.length(), FluidCollisionMode.NEVER, true) != null) continue;
            position.setYaw((float) Math.toDegrees(yaw) + 180);
            return position;
        }
        return null;
    }

    private void interact(Player player, Object handle) {
        Chest chest = chests.get(player.getUniqueId());
        if (chest == null || chest.owner() != player || !chest.entityId().equals(Ady.id(handle))
            || !chest.clickQueued().compareAndSet(false, true)) return;
        // Adyeshach may call from a packet thread or report both hands. Recheck after dispatch to the game thread.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            chest.clickQueued().set(false);
            if (chests.get(player.getUniqueId()) == chest && inReach(player)) chest.onClick().accept(player);
        });
    }

    boolean inReach(Player player) {
        Chest chest = chests.get(player.getUniqueId());
        if (chest == null || chest.owner() != player || !player.isOnline() || player.isDead()) return false;
        Location here = player.getLocation();
        return chest.position().getWorld().equals(here.getWorld()) && here.distanceSquared(chest.position()) <= REACH_SQUARED;
    }

    void remove(Player owner) {
        Chest chest = chests.get(owner.getUniqueId());
        if (chest != null && chest.owner() == owner && chests.remove(owner.getUniqueId(), chest)) {
            bridge.removePrivateMarker(owner, chest.marker());
        }
    }

    void shutdown() {
        closed = true;
        for (Chest chest : List.copyOf(chests.values())) remove(chest.owner());
        if (listening) bridge.shutdown();
        listening = false;
    }

    // Loaded only after the optional Adyeshach bridge has initialized.
    private static final class Ady {
        static String configure(Object handle, String modelId, double scale) {
            if (!(handle instanceof ink.ptms.adyeshach.common.entity.type.AdyArmorStand npc)) return null;
            // The navigation marker defaults to a zero-size hitbox. A small, non-marker stand receives use-entity clicks.
            npc.setMarker(false);
            npc.setSmall(true);
            npc.setBasePlate(false);
            // ArcartX's Adyeshach listener reads these tags again on ClientEntityJoinEvent after a re-render.
            npc.setPersistentTag("ArcartX_Scale", Double.toString(scale));
            npc.setPersistentTag("ArcartX_Model", modelId);
            npc.setPersistentTag("ArcartX_Width", Double.toString(0.9 * scale));
            npc.setPersistentTag("ArcartX_Height", Double.toString(0.9 * scale));
            return npc.getUniqueId();
        }

        static String id(Object handle) {
            return handle instanceof ink.ptms.adyeshach.common.entity.EntityBase npc ? npc.getUniqueId() : null;
        }
    }
}
