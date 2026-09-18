package xuanmo.arcartxsuite.bridge;

import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.arcartx.api.ArcartXAPI;
import priv.seventeen.artist.arcartx.core.effect.ArcartXEffectManager;
import priv.seventeen.artist.arcartx.core.effect.data.EffectPosition;
import priv.seventeen.artist.arcartx.core.effect.data.WorldTextureBuilder;
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager;
import priv.seventeen.artist.arcartx.core.entity.data.ArcartXPlayer;
import xuanmo.arcartxsuite.api.bridge.WorldTexturePulse;

/**
 * {@link xuanmo.arcartxsuite.api.bridge.WorldTextureBridgeAPI} 的默认实现，
 * 桥接 ArcartX 世界贴图特效，用于在世界上方渲染悬浮纹理/文字。
 */
public class ArcartXWorldTextureService implements xuanmo.arcartxsuite.api.bridge.WorldTextureBridgeAPI {

    private final JavaPlugin plugin;
    private final Logger logger;

    private boolean available;
    private ArcartXEntityManager entityManager;

    public ArcartXWorldTextureService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = xuanmo.arcartxsuite.module.AxsLog.logger();
    }

    @Override
    public boolean initialize() {
        available = false;
        entityManager = null;
        Plugin arcartX = plugin.getServer().getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            return false;
        }
        try {
            entityManager = ArcartXAPI.getEntityManager();
        } catch (RuntimeException exception) {
            logger.warning("ArcartX WorldTexture 桥接初始化失败: " + exception.getMessage());
            return false;
        }
        if (entityManager == null) {
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
        if (!available || entity == null) return;
        try {
            removeForNearby(entity.getLocation(), id);
        } catch (RuntimeException exception) {
            logger.warning("WorldTexture 移除失败 (" + id + "): " + exception.getMessage());
        }
    }

    @Override
    public void removeFromWorld(World world, String id, Location location) {
        if (!available || location == null) return;
        try {
            removeForNearby(location, id);
        } catch (RuntimeException exception) {
            logger.warning("WorldTexture 移除失败 (" + id + "): " + exception.getMessage());
        }
    }

    @Override
    public boolean spawnForPlayer(Player player, String id, Location location,
                                  String texture, double width, double height,
                                  float yaw, float pitch, boolean facing, boolean glowing,
                                  int lifeTime, WorldTexturePulse pulse) {
        if (!available || player == null || location == null || entityManager == null) return false;
        try {
            ArcartXPlayer handler = entityManager.getPlayer(player);
            if (handler == null) return false;
            WorldTextureBuilder builder = new WorldTextureBuilder();
            builder.setTexture(texture, facing, glowing);
            builder.setSize(width, height);
            builder.setLifeTime(lifeTime);
            if (pulse != null) {
                builder.addScaleAnimation(pulse.delay(), pulse.from(), pulse.to(), pulse.duration(), pulse.loop());
            }
            EffectPosition position = EffectPosition.location(
                location.getX(), location.getY(), location.getZ(), pitch, yaw);
            handler.addWorldTexture(id, builder, position);
            return true;
        } catch (RuntimeException exception) {
            logger.warning("WorldTexture 生成失败 (" + id + "): " + exception.getMessage());
            return false;
        }
    }

    @Override
    public boolean removeForPlayer(Player player, String id) {
        if (!available || player == null || entityManager == null) return false;
        try {
            ArcartXPlayer handler = entityManager.getPlayer(player);
            if (handler == null) return false;
            handler.removeWorldTexture(id);
            return true;
        } catch (RuntimeException exception) {
            logger.warning("WorldTexture 移除失败 (" + id + "): " + exception.getMessage());
            return false;
        }
    }

    /**
     * 向指定位置 128 格内的所有玩家发送 WORLD_TEXTURE_REMOVE。
     * 与 ArcartX 自身的广播半径（getNearbyEntities 128）保持一致。
     */
    private void removeForNearby(Location anchor, String id) {
        if (anchor == null || anchor.getWorld() == null) return;
        for (Entity nearby : anchor.getWorld().getNearbyEntities(anchor, 128.0, 128.0, 128.0)) {
            if (!(nearby instanceof Player viewer)) {
                continue;
            }
            ArcartXPlayer handler = entityManager.getPlayer(viewer);
            if (handler != null) {
                handler.removeWorldTexture(id);
            }
        }
    }
}
