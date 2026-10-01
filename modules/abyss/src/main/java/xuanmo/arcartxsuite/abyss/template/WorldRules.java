package xuanmo.arcartxsuite.abyss.template;

import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 副本世界治理规则（BTDungeons 同款思路）：
 * 按副本覆盖游戏规则 + 自然刷怪控制 + 副本级命令控制。
 *
 * @param gamerules             副本世界游戏规则覆盖（键=GameRule 名，值=布尔或整数）
 * @param disableNaturalSpawn   是否禁止环境驱动刷怪（插件/命令/刷怪蛋不受影响）
 * @param disabledSpawnReasons  额外禁用的生成来源（CreatureSpawnEvent.SpawnReason 名）
 * @param commandControl        副本级命令控制（覆盖全局 restrictions.command-mode）
 */
public record WorldRules(
    Map<String, String> gamerules,
    boolean disableNaturalSpawn,
    List<String> disabledSpawnReasons,
    CommandControl commandControl
) {

    /**
     * 副本级命令控制。
     * <p>
     * {@code mode=INHERIT} 时沿用全局 restrictions；BLACKLIST/WHITELIST 时
     * 以 {@code commands} 列表为副本级黑/白名单（独立于全局列表）。
     */
    public record CommandControl(ControlMode mode, List<String> commands) {

        public enum ControlMode { INHERIT, BLACKLIST, WHITELIST }

        public static final CommandControl INHERIT =
            new CommandControl(ControlMode.INHERIT, List.of());

        static CommandControl load(ConfigurationSection s) {
            if (s == null) return INHERIT;
            ControlMode mode;
            try {
                mode = ControlMode.valueOf(
                    s.getString("mode", "INHERIT").toUpperCase());
            } catch (IllegalArgumentException e) {
                mode = ControlMode.INHERIT;
            }
            return new CommandControl(mode, s.getStringList("commands"));
        }
    }

    public static final WorldRules NONE =
        new WorldRules(Map.of(), false, List.of(), CommandControl.INHERIT);

    public static WorldRules load(ConfigurationSection worldSection) {
        if (worldSection == null) return NONE;
        Map<String, String> gamerules = new java.util.LinkedHashMap<>();
        var gs = worldSection.getConfigurationSection("gamerules");
        if (gs == null) {
            gs = worldSection.getConfigurationSection("gamerule");
        }
        if (gs != null) {
            for (String k : gs.getKeys(false)) {
                gamerules.put(k, gs.getString(k));
            }
        }
        return new WorldRules(
            gamerules,
            worldSection.getBoolean("disable_natural_spawn",
                worldSection.getBoolean("disable-natural-spawn", false)),
            worldSection.getStringList("disabled_spawn_reasons"),
            CommandControl.load(worldSection.getConfigurationSection("command_control"))
        );
    }
}
