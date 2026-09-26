package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * Abyss 地牢模块的公开查询能力。
 * <p>
 * 其他模块（battlepass/questgps/entitytracker 等）经
 * {@code getCapability(AbyssQueryable.class)} 查询玩家地牢状态；
 * abyss 模块未启用时返回 null。
 */
@ApiStability.Stable
public interface AbyssQueryable {

    /** 玩家是否在地牢副本中。 */
    boolean isInDungeon(UUID player);

    /** 玩家当前地牢 id（不在副本返回 null）。 */
    @Nullable String currentDungeonId(UUID player);

    /** 玩家当前副本运行秒数（不在副本返回 0）。 */
    long currentRunningSeconds(UUID player);

    /** 玩家某副本冷却剩余秒数。 */
    long cooldownRemaining(UUID player, String dungeonId);

    /** 玩家体力（体力系统关闭返回 -1）。 */
    int stamina(UUID player);

    /** 玩家某副本通关次数。 */
    long completions(UUID player, String dungeonId);

    /** 玩家某副本最佳通关秒数（无记录返回 0）。 */
    long bestTime(UUID player, String dungeonId);

    /** 全部地牢模板 id。 */
    List<String> dungeonIds();
}
