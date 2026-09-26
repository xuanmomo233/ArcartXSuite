package xuanmo.arcartxsuite.abyss.mob;

import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;

/**
 * 原版实体兜底来源：mobId 按 {@link EntityType} 名匹配。
 */
public final class VanillaMobProvider implements MobProvider {

    private final Logger logger;

    public VanillaMobProvider(Logger logger) {
        this.logger = logger;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public @Nullable Entity spawn(String mobId, Location location, double level) {
        try {
            EntityType type = EntityType.valueOf(mobId.toUpperCase());
            return location.getWorld().spawnEntity(location, type);
        } catch (IllegalArgumentException e) {
            logger.warning("[abyss] 未知原版实体类型: " + mobId
                + "（MythicMobs 未安装，无法按 MM id 生成）");
            return null;
        } catch (Throwable t) {
            logger.warning("[abyss] 原版实体生成失败 " + mobId + ": " + t.getMessage());
            return null;
        }
    }
}
