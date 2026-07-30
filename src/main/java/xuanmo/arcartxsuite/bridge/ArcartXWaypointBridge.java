package xuanmo.arcartxsuite.bridge;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.arcartx.api.ArcartXAPI;
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager;
import priv.seventeen.artist.arcartx.core.entity.data.ArcartXPlayer;

public final class ArcartXWaypointBridge implements xuanmo.arcartxsuite.api.bridge.WaypointBridgeAPI {

    private final JavaPlugin plugin;
    private final Set<String> availableStyleIds = new LinkedHashSet<>();

    private boolean available;
    private ArcartXEntityManager entityManager;

    public ArcartXWaypointBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean initialize(String ownerLabel) {
        reset();

        Plugin arcartX = Bukkit.getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning(ownerLabel + " 初始化失败: 未找到 ArcartX。");
            return false;
        }

        try {
            entityManager = ArcartXAPI.getEntityManager();
            if (entityManager == null) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning(ownerLabel + " 初始化失败: EntityManager 不可用。");
                return false;
            }
            loadAvailableStyleIds(arcartX.getDataFolder());
            available = true;
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning(ownerLabel + " 初始化失败: " + exception.getMessage());
            return false;
        }
    }

    @Override
    public void shutdown() {
        reset();
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public Set<String> availableStyleIds() {
        return Set.copyOf(availableStyleIds);
    }

    @Override
    public String resolveStyleId(String preferredStyleId, String fallbackStyleId, String ownerLabel) {
        String preferred = safe(preferredStyleId).isBlank() ? safe(fallbackStyleId) : preferredStyleId.trim();
        if (availableStyleIds.isEmpty() || availableStyleIds.contains(normalize(preferred))) {
            return preferred;
        }
        if (!"default".equalsIgnoreCase(preferred) && availableStyleIds.contains("default")) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning(ownerLabel + " 路标样式不存在，已回退 default: " + preferred);
            return "default";
        }
        return preferred;
    }

    @Override
    public boolean addWaypoint(
        Player player,
        String waypointId,
        String title,
        String styleId,
        double x,
        double y,
        double z
    ) {
        if (!available || player == null || entityManager == null) return false;
        try {
            ArcartXPlayer handler = entityManager.getPlayer(player);
            if (handler == null) return false;
            handler.addWayPoint(waypointId, title, styleId, x, y, z);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartX waypoint 创建失败: " + exception.getMessage());
            return false;
        }
    }

    @Override
    public boolean removeWaypoint(Player player, String waypointId, boolean animated) {
        if (!available || player == null || safe(waypointId).isBlank() || entityManager == null) return false;
        try {
            ArcartXPlayer handler = entityManager.getPlayer(player);
            if (handler == null) return false;
            handler.deleteWayPoint(waypointId, animated);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartX waypoint 删除失败: " + exception.getMessage());
            return false;
        }
    }

    @Override
    public boolean clearWaypoints(Player player) {
        if (!available || player == null || entityManager == null) return false;
        try {
            ArcartXPlayer handler = entityManager.getPlayer(player);
            if (handler == null) return false;
            handler.clearWayPoint();
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartX waypoint 清理失败: " + exception.getMessage());
            return false;
        }
    }

    private void loadAvailableStyleIds(File arcartXDataFolder) {
        File waypointFolder = new File(arcartXDataFolder, "waypoint");
        if (!waypointFolder.isDirectory()) {
            return;
        }
        File[] files = waypointFolder.listFiles(file -> file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String key : yaml.getKeys(false)) {
                availableStyleIds.add(normalize(key));
            }
        }
    }

    private void reset() {
        available = false;
        entityManager = null;
        availableStyleIds.clear();
    }

    private static String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
