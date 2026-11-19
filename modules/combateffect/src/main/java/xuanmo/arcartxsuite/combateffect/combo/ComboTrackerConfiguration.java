package xuanmo.arcartxsuite.combateffect.combo;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 连击追踪模块的配置记录。
 * <p>
 * 属于 combateffect 模块的 combo 子模块，负责定义连击检测的启用状态、
 * 伤害来源、超时时间、Chronos 分组、变量同步等参数。
 *
 * @param enabled              是否启用连击追踪
 * @param source               连击伤害来源类型（AUTO / CHRONOS / BUKKIT）
 * @param timeoutMs            连击超时时间（毫秒），超过则重置连击计数
 * @param chronosGroups        Chronos 事件分组名称集合
 * @param syncVariable         是否实时同步连击数
 * @param perTarget            是否按目标分别计数连击
 * @param debug                是否开启调试日志
 */
public record ComboTrackerConfiguration(
    boolean enabled,
    ComboSource source,
    long timeoutMs,
    Set<String> chronosGroups,
    boolean syncVariable,
    boolean perTarget,
    boolean debug
) {

    /**
     * 连击伤害来源类型枚举。
     * <p>
     * AUTO 在 Symphony 确认桥可用时选择有效伤害，否则优先 Chronos。
     * CHRONOS 统计状态段，BUKKIT 统计宿主结算的有效伤害。
     */
    public enum ComboSource {
        AUTO, CHRONOS, BUKKIT;

        /**
         * 从字符串解析连击来源类型，无法识别时返回 {@link #AUTO}。
         *
         * @param value 配置字符串，大小写不敏感
         * @return 对应的连击来源类型
         */
        public static ComboSource from(String value) {
            if (value == null) return AUTO;
            return switch (value.trim().toLowerCase()) {
                case "chronos" -> CHRONOS;
                case "bukkit" -> BUKKIT;
                default -> AUTO;
            };
        }
    }

    /**
     * 从配置节点加载连击追踪配置。
     * <p>
     * 当配置节点为 {@code null} 时返回全部禁用的默认配置。
     *
     * @param section Bukkit 配置节点
     * @return 加载后的配置记录
     */
    public static ComboTrackerConfiguration load(ConfigurationSection section) {
        if (section == null) {
            return new ComboTrackerConfiguration(false, ComboSource.AUTO, 2000L, Set.of(), false, false, false);
        }
        boolean enabled = section.getBoolean("enabled", false);
        ComboSource source = ComboSource.from(section.getString("source", "auto"));
        long timeoutMs = Math.max(200L, section.getLong("timeout", 2000L));
        Set<String> chronosGroups = new HashSet<>(section.getStringList("chronos-groups"));
        if (chronosGroups.isEmpty()) {
            chronosGroups.add("攻击");
        }
        boolean syncVariable = section.getBoolean("sync-variable", false);
        boolean perTarget = section.getBoolean("per-target", false);
        boolean debug = section.getBoolean("debug", false);
        return new ComboTrackerConfiguration(enabled, source, timeoutMs, chronosGroups, syncVariable, perTarget, debug);
    }
}
