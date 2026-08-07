package xuanmo.arcartxsuite.entitytracker.config;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 跨服排名设置，属于 entitytracker 模块的新功能配置子系统。
 * <p>
 * 配置跨服 Boss 排名的开关、更新间隔、排名类型和最大条目数，
 * 支持最高伤害、Boss 伤害、击杀数、参与度、服务器维度等排名类型。
 *
 * @param enabled              是否启用跨服排名
 * @param updateIntervalSeconds 更新间隔秒数（最小 15 秒）
 * @param rankingTypes         排名类型列表
 * @param maxEntries           最大条目数（最小 1）
 */
public record CrossServerRankingSettings(
    boolean enabled,
    int updateIntervalSeconds,
    List<String> rankingTypes,
    int maxEntries
) {

    public static final String TYPE_BEST_DAMAGE = "best_damage";
    public static final String TYPE_BOSS_DAMAGE = "boss_damage";
    public static final String TYPE_KILLS = "kills";
    public static final String TYPE_PARTICIPATE = "participate";
    public static final String TYPE_SERVER = "server";

    /**
     * 返回未启用的默认设置。
     *
     * @return 禁用设置
     */
    public static CrossServerRankingSettings disabled() {
        return new CrossServerRankingSettings(false, 60, List.of(TYPE_BEST_DAMAGE), 50);
    }

    /**
     * 从配置节加载跨服排名设置。
     *
     * @param section 配置节，为 {@code null} 时返回禁用设置
     * @return 解析得到的设置
     */
    public static CrossServerRankingSettings load(ConfigurationSection section) {
        if (section == null) {
            return disabled();
        }
        List<String> types = section.getStringList("ranking-types");
        if (types.isEmpty()) {
            types = List.of(TYPE_BEST_DAMAGE);
        }
        return new CrossServerRankingSettings(
            section.getBoolean("enabled", false),
            Math.max(15, section.getInt("update-interval", 60)),
            List.copyOf(new ArrayList<>(types)),
            Math.max(1, section.getInt("max-entries", 50))
        );
    }
}
