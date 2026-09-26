package xuanmo.arcartxsuite.abyss.mob;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 怪物来源抽象：MythicMobs / 原版实体。
 * <p>
 * MythicMobs 未安装时自动降级为原版实体生成（按 mobId 尝试 EntityType 匹配），
 * 保证地牢基础玩法可用。
 */
public interface MobProvider {

    /** 来源是否可用。 */
    boolean available();

    /**
     * 生成一只怪。
     *
     * @param mobId    怪 id（MythicMobs 内部名 或 原版 EntityType 名）
     * @param location 生成位置
     * @param level    MM 等级（原版忽略）
     * @return 实体；失败 null
     */
    @Nullable Entity spawn(String mobId, Location location, double level);

    /**
     * 释放 MM 技能（cast_skill 动作用）。
     *
     * @param caster 施法者（玩家）
     * @param skill  技能名
     * @param power  威力系数
     * @return 是否成功调用
     */
    default boolean castSkill(Player caster, String skill, double power) {
        return false;
    }
}
