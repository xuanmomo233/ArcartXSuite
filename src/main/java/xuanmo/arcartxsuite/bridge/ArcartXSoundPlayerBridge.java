package xuanmo.arcartxsuite.bridge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.arcartx.api.ArcartXAPI;
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager;
import priv.seventeen.artist.arcartx.core.entity.data.ArcartXPlayer;
import priv.seventeen.artist.arcartx.core.sound.ArcartXSoundPlayer;
import xuanmo.arcartxsuite.api.bridge.SoundPlayerBridgeAPI;

public final class ArcartXSoundPlayerBridge implements SoundPlayerBridgeAPI {

    private final JavaPlugin plugin;
    private boolean available;
    private ArcartXEntityManager entityManager;

    public ArcartXSoundPlayerBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean initialize() {
        reset();

        Plugin arcartX = Bukkit.getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartXSoundPlayerBridge 初始化失败: 未找到 ArcartX。");
            return false;
        }

        try {
            entityManager = ArcartXAPI.getEntityManager();
            if (entityManager == null) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartXSoundPlayerBridge: EntityManager 不可用。");
                return false;
            }
            available = true;
            return true;
        } catch (RuntimeException e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartXSoundPlayerBridge 初始化失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void shutdown() {
        reset();
    }

    @Override
    public boolean playSoundForPlayer(Player player, Location location,
                                       String resourcePath, String category,
                                       int distOrRoll, double pitch, int keepTimeMs) {
        if (!available || player == null || !player.isOnline() || location == null) return false;
        try {
            ArcartXSoundPlayer.playSoundForPlayer(
                location.getWorld(), player, location,
                resourcePath, category, distOrRoll, pitch, keepTimeMs);
            return true;
        } catch (RuntimeException e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartXSoundPlayerBridge.playSoundForPlayer 失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean playSoundForSelf(Player player, String resourcePath,
                                     String category, float pitch, int keepTimeMs) {
        if (!available || player == null || !player.isOnline() || entityManager == null) return false;
        try {
            ArcartXPlayer axPlayer = entityManager.getPlayer(player);
            if (axPlayer == null) return false;
            axPlayer.playSoundForSelf(resourcePath, category, pitch, keepTimeMs);
            return true;
        } catch (RuntimeException e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartXSoundPlayerBridge.playSoundForSelf 失败: " + e.getMessage());
            return false;
        }
    }

    private void reset() {
        available = false;
        entityManager = null;
    }
}
