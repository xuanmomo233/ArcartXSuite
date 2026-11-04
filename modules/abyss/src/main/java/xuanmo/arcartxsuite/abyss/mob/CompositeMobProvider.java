package xuanmo.arcartxsuite.abyss.mob;

import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 复合怪物来源：按 mobId 前缀路由。
 * <ul>
 *   <li>{@code vanilla:<EntityType>} —— 强制原版实体（无 MM 也可用）</li>
 *   <li>{@code mythic:<MM怪名>} / 无前缀 —— MythicMobs（不可用时回退原版）</li>
 * </ul>
 * 保证装了 MM 的地牢可混用原版怪，没装 MM 时 vanilla: 怪照常工作。
 */
public final class CompositeMobProvider implements MobProvider {

    private final @Nullable MobProvider mythic;
    private final MobProvider vanilla;
    private final Logger logger;

    public CompositeMobProvider(@Nullable MobProvider mythic, MobProvider vanilla, Logger logger) {
        this.mythic = mythic;
        this.vanilla = vanilla;
        this.logger = logger;
    }

    @Override
    public boolean available() {
        return true;
    }

    /** MythicMobs 通道是否可用。 */
    public boolean hasMythic() {
        return mythic != null && mythic.available();
    }

    @Override
    public @Nullable Entity spawn(String mobId, Location location, double level) {
        var spawned = spawnWithSource(mobId, location, level);
        return spawned == null ? null : spawned.entity();
    }

    record Spawned(Entity entity, String source) { }

    @Nullable Spawned spawnWithSource(String mobId, Location location, double level) {
        if (mobId == null || mobId.isBlank()) return null;
        String id = mobId.trim();
        String lower = id.toLowerCase(java.util.Locale.ROOT);

        if (lower.startsWith("vanilla:")) {
            Entity entity = vanilla.spawn(id.substring(8), location, level);
            return entity == null ? null : new Spawned(entity, "vanilla");
        }
        if (lower.startsWith("mythic:")) {
            id = id.substring(7);
        }
        if (mythic != null && mythic.available()) {
            Entity e = mythic.spawn(id, location, level);
            if (e != null) return new Spawned(e, "mythic");
        }
        // MM 不可用/生成失败：尝试按原版 EntityType 兜底
        Entity entity = vanilla.spawn(id, location, level);
        return entity == null ? null : new Spawned(entity, "vanilla");
    }

    @Override
    public boolean castSkill(Player caster, String skill, double power) {
        return mythic != null && mythic.available() && mythic.castSkill(caster, skill, power);
    }
}
