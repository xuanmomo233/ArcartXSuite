package xuanmo.arcartxsuite.abyss.mob;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * MythicMobs 怪物来源。
 * <p>
 * <b>隔离原则</b>：仅在 {@code MythicBukkit} 已启用时才应实例化本类；
 * 类字段/方法签名直接引用 MM 类型，类加载即意味着 MM 在 classpath。
 * 外层（AbyssService）按 battlepass 的"可选依赖"模式守卫注册。
 * 兼容 MM 5.x（io.lumine.mythic.api.*）。
 */
public final class MythicMobProvider implements MobProvider {

    private final Logger logger;

    public MythicMobProvider(Logger logger) {
        this.logger = logger;
    }

    @Override
    public boolean available() {
        try {
            return MythicBukkit.inst() != null
                && MythicBukkit.inst().getMobManager() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public @Nullable Entity spawn(String mobId, Location location, double level) {
        try {
            Optional<MythicMob> mob = MythicBukkit.inst().getMobManager().getMythicMob(mobId);
            if (mob.isEmpty()) {
                logger.warning("[abyss] MythicMob 不存在: " + mobId);
                return null;
            }
            ActiveMob active = mob.get().spawn(
                BukkitAdapter.adapt(location), Math.max(1, level));
            return active != null ? BukkitAdapter.adapt(active.getEntity()) : null;
        } catch (Throwable t) {
            logger.warning("[abyss] MythicMob 生成失败 " + mobId + ": " + t.getMessage());
            return null;
        }
    }

    @Override
    public boolean castSkill(Player caster, String skill, double power) {
        try {
            var api = MythicBukkit.inst().getAPIHelper();
            return api.castSkill(caster, skill, caster.getLocation(), (float) power);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 实体是否 MM 怪。 */
    public boolean isMythicMob(Entity entity) {
        try {
            return MythicBukkit.inst().getMobManager().isMythicMob(entity);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 取实体的 ActiveMob（用于归属怪物组判定）。 */
    public @Nullable ActiveMob getActiveMob(Entity entity) {
        try {
            return MythicBukkit.inst().getMobManager().getMythicMobInstance(entity);
        } catch (Throwable t) {
            return null;
        }
    }
}
