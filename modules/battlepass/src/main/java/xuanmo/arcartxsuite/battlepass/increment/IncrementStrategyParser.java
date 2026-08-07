package xuanmo.arcartxsuite.battlepass.increment;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 增量策略解析器，从 YAML 配置节解析 {@link IncrementStrategy}。
 * <p>
 * 根据 {@code type} 字段路由到具体实现类（{@link FixedIncrementStrategy}、{@link PayloadValueStrategy}），
 * 未知类型或 null 入参时回退为默认的固定增量策略。
 */
public final class IncrementStrategyParser {

    private IncrementStrategyParser() {}

    /**
     * 从配置节解析增量策略。
     *
     * @param section 策略配置节，可为 null（回退为固定增量 1）
     * @return 解析得到的策略实例
     */
    public static IncrementStrategy parse(ConfigurationSection section) {
        if (section == null) return new FixedIncrementStrategy(1);
        String type = section.getString("type", "fixed");
        return switch (type) {
            case "fixed" -> new FixedIncrementStrategy(section.getInt("value", 1));
            case "payload_value" -> new PayloadValueStrategy(
                section.getString("payload-key", ""),
                section.getInt("max-per-event", 0),
                section.getDouble("scale", 1.0)
            );
            default -> new FixedIncrementStrategy(1);
        };
    }
}
