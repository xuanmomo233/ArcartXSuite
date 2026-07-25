package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.SoundPlayerBridgeAPI;

/**
 * ArcartX SoundPlayer 反射桥接实现。
 * <p>
 * 通过 {@code ArcartXAPI.getSoundPlayer()} 获取音效播放器实例，
 * 调用 {@code ArcartXSoundPlayer.playSoundForPlayer()} / {@code ArcartXPlayer.playSoundForSelf()} 播放自定义音效。
 */
public final class ArcartXSoundPlayerBridge implements SoundPlayerBridgeAPI {

    private static final String ARCARTX_API_CLASS = "priv.seventeen.artist.arcartx.api.ArcartXAPI";
    private static final String SOUND_PLAYER_CLASS = "priv.seventeen.artist.arcartx.api.ArcartXSoundPlayer";
    private static final String ENTITY_MANAGER_CLASS = "priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager";
    private static final String ARCARTX_PLAYER_CLASS = "priv.seventeen.artist.arcartx.core.entity.data.ArcartXPlayer";

    private final JavaPlugin plugin;
    private boolean available;
    private Object soundPlayer;
    private Method playSoundForPlayerMethod;
    private Method getOrCreateEntityMethod;
    private Method playSoundForSelfMethod;

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
            ClassLoader classLoader = arcartX.getClass().getClassLoader();
            Class<?> apiClass = Class.forName(ARCARTX_API_CLASS, true, classLoader);

            // ArcartXAPI.getSoundPlayer()
            Method getSoundPlayer = apiClass.getMethod("getSoundPlayer");
            soundPlayer = getSoundPlayer.invoke(null);
            if (soundPlayer == null) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartXSoundPlayerBridge: getSoundPlayer() 返回 null。");
                return false;
            }

            // ArcartXSoundPlayer.playSoundForPlayer(World, Player, Location, String, String, int, double, int)
            Class<?> soundPlayerClass = Class.forName(SOUND_PLAYER_CLASS, true, classLoader);
            playSoundForPlayerMethod = findStaticMethod(soundPlayerClass, "playSoundForPlayer",
                8, org.bukkit.World.class, Player.class, Location.class,
                String.class, String.class, int.class, double.class, int.class);

            // ArcartXEntityManager.getOrCreateEntity(Entity)
            Class<?> entityManagerClass = Class.forName(ENTITY_MANAGER_CLASS, true, classLoader);
            getOrCreateEntityMethod = entityManagerClass.getMethod("getOrCreateEntity", org.bukkit.entity.Entity.class);

            // ArcartXPlayer.playSoundForSelf(String, String, float, int)
            Class<?> arcartxPlayerClass = Class.forName(ARCARTX_PLAYER_CLASS, true, classLoader);
            playSoundForSelfMethod = arcartxPlayerClass.getMethod("playSoundForSelf",
                String.class, String.class, float.class, int.class);

            available = playSoundForPlayerMethod != null || playSoundForSelfMethod != null;
            if (!available) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartXSoundPlayerBridge: 未找到可用的音效播放方法。");
            }
            return available;
        } catch (ReflectiveOperationException e) {
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
        if (playSoundForPlayerMethod == null) return false;
        try {
            playSoundForPlayerMethod.invoke(null,
                location.getWorld(), player, location,
                resourcePath, category, distOrRoll, pitch, keepTimeMs);
            return true;
        } catch (ReflectiveOperationException e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartXSoundPlayerBridge.playSoundForPlayer 失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean playSoundForSelf(Player player, String resourcePath,
                                     String category, float pitch, int keepTimeMs) {
        if (!available || player == null || !player.isOnline()) return false;
        if (playSoundForSelfMethod == null || getOrCreateEntityMethod == null) return false;
        try {
            Object axPlayer = getOrCreateEntityMethod.invoke(null, player);
            if (axPlayer == null) return false;
            playSoundForSelfMethod.invoke(axPlayer, resourcePath, category, pitch, keepTimeMs);
            return true;
        } catch (ReflectiveOperationException e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartXSoundPlayerBridge.playSoundForSelf 失败: " + e.getMessage());
            return false;
        }
    }

    private void reset() {
        available = false;
        soundPlayer = null;
        playSoundForPlayerMethod = null;
        getOrCreateEntityMethod = null;
        playSoundForSelfMethod = null;
    }

    private static Method findStaticMethod(Class<?> clazz, String name, int paramCount, Class<?>... paramTypes) {
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            // 尝试按参数数量匹配
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                    return m;
                }
            }
            return null;
        }
    }
}
