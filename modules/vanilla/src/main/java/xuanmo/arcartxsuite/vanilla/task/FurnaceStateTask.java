package xuanmo.arcartxsuite.vanilla.task;

import org.bukkit.Bukkit;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每 tick 轮询玩家打开的熔炉 BlockEntity 状态，通过 PacketBridgeAPI 推送到 UI。
 */
public final class FurnaceStateTask {

    private final Plugin plugin;
    private final PacketBridgeAPI packetBridge;
    private final String furnaceUiId;
    private BukkitTask task;

    // 玩家 UUID -> 熔炉方块坐标 key
    private final Map<UUID, String> trackedFurnaces = new ConcurrentHashMap<>();
    // 熔炉方块坐标 key -> 最大燃烧时间
    private final Map<String, Integer> maxBurnTimes = new ConcurrentHashMap<>();

    public FurnaceStateTask(Plugin plugin, PacketBridgeAPI packetBridge, String furnaceUiId) {
        this.plugin = plugin;
        this.packetBridge = packetBridge;
        this.furnaceUiId = furnaceUiId;
    }

    public void start(int intervalTicks) {
        if (task != null) {
            task.cancel();
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        trackedFurnaces.clear();
        maxBurnTimes.clear();
    }

    public void trackPlayer(UUID playerId, String furnaceKey) {
        trackedFurnaces.put(playerId, furnaceKey);
    }

    public void untrackPlayer(UUID playerId) {
        trackedFurnaces.remove(playerId);
    }

    private void tick() {
        for (Map.Entry<UUID, String> entry : trackedFurnaces.entrySet()) {
            UUID playerId = entry.getKey();
            String furnaceKey = entry.getValue();
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                trackedFurnaces.remove(playerId);
                continue;
            }

            Furnace furnace = resolveFurnace(furnaceKey);
            if (furnace == null) {
                continue;
            }

            int burnTime = furnace.getBurnTime();
            int prevMax = maxBurnTimes.getOrDefault(furnaceKey, 0);
            // burnTime 增大说明新燃料被添加，更新最大值
            if (burnTime > prevMax || burnTime == 0) {
                maxBurnTimes.put(furnaceKey, burnTime);
                prevMax = burnTime;
            }

            Map<String, Object> packet = new LinkedHashMap<>();
            packet.put("burnTime", burnTime);
            packet.put("burnTimeTotal", Math.max(prevMax, 1));
            packet.put("cookTime", furnace.getCookTime());
            packet.put("cookTimeTotal", Math.max(furnace.getCookTimeTotal(), 1));
            // 火焰图标可见性：burnTime > 0 表示正在燃烧
            packet.put("burning", burnTime > 0);
            packetBridge.sendPacket(player, furnaceUiId, "furnace_state", packet);
        }
    }

    private Furnace resolveFurnace(String key) {
        // key 格式: worldName,x,y,z
        String[] parts = key.split(",");
        if (parts.length != 4) {
            return null;
        }
        try {
            var world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                return null;
            }
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            var block = world.getBlockAt(x, y, z);
            if (block.getState() instanceof Furnace) {
                return (Furnace) block.getState();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String buildFurnaceKey(org.bukkit.Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
