package xuanmo.arcartxsuite.bridge;

import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.arcartx.core.effect.ArcartXEffectManager;
import priv.seventeen.artist.arcartx.core.effect.data.EffectPosition;
import priv.seventeen.artist.arcartx.core.effect.data.WorldTextureBuilder;

/**
 * {@link xuanmo.arcartxsuite.api.bridge.WorldTextureBridgeAPI} 的默认实现，
 * 桥接 ArcartX 世界贴图特效，用于在世界上方渲染悬浮纹理/文字。
 */
public class ArcartXWorldTextureService implements xuanmo.arcartxsuite.api.bridge.WorldTextureBridgeAPI {

    private final JavaPlugin plugin;
    private final Logger logger;

    private boolean available;

    public ArcartXWorldTextureService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = xuanmo.arcartxsuite.module.AxsLog.logger();
    }

    @Override
    public boolean initialize() {
        available = false;
        Plugin arcartX = plugin.getServer().getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            return false;
        }
        available = true;
        logger.fine("ArcartX WorldTexture 桥接初始化成功。");
        return true;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void spawnOnEntity(Entity entity, String id, String texture,
                              double width, double height, double offsetY, boolean billboard) {
        if (!available) return;
        try {
            WorldTextureBuilder builder = new WorldTextureBuilder();
            builder.setTexture(texture, true, true);
            builder.setSize(width, height);
            builder.setLifeTime(-1);
            EffectPosition position = EffectPosition.followEntity(entity, 0.0, offsetY, 0.0, 0f, 0f, 0f, false, billboard);
            ArcartXEffectManager.spawnWorldTextureEffect(entity, id, builder, position);
        } catch (RuntimeException exception) {
            logger.warning("WorldTexture 生成失败 (" + id + "): " + exception.getMessage());
        }
    }

    @Override
    public void spawnAtLocation(World world, Location location, String id, String texture,
                                double width, double height) {
        if (!available) return;
        try {
            WorldTextureBuilder builder = new WorldTextureBuilder();
            builder.setTexture(texture, true, true);
            builder.setSize(width, height);
            builder.setLifeTime(-1);
            EffectPosition position = EffectPosition.location(location);
            ArcartXEffectManager.spawnWorldTextureEffect(world, location, id, builder, position);
        } catch (RuntimeException exception) {
            logger.warning("WorldTexture 生成失败 (" + id + "): " + exception.getMessage());
        }
    }

    @Override
    public void removeFromEntity(Entity entity, String id) {
        if (!available) return;
        try {
            ArcartXEffectManager.removeModelEffect(entity, id);
        } catch (RuntimeException exception) {
            logger.warning("WorldTexture 移除失败 (" + id + "): " + exception.getMessage());
        }
    }

    @Override
    public void removeFromWorld(World world, String id, Location location) {
        if (!available) return;
        try {
            ArcartXEffectManager.removeModelEffect(world, id, location);
        } catch (RuntimeException exception) {
            logger.warning("WorldTexture 移除失败 (" + id + "): " + exception.getMessage());
        }
    }
}
