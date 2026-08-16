package xuanmo.arcartxsuite.battlepass.increment;

import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 增量策略解析器，从 YAML 配置节解析 {@link IncrementStrategy}。
 */
public final class IncrementStrategyParser {

    private static final String SUPPORTED_TYPES = "fixed, payload_value";

    private IncrementStrategyParser() {}

    /**
     * 从配置节解析增量策略。
     *
     * @param section 策略配置节，可为 null
     * @param logger 日志记录器
     * @param taskId 任务 ID
     * @return 解析得到的策略实例
     */
    public static IncrementStrategy parse(
        ConfigurationSection section, Logger logger, String taskId
    ) {
        if (section == null) return new FixedIncrementStrategy(1);
        String type = section.getString("type", "fixed");
        return switch (type) {
            case "fixed" -> {
                int value = section.getInt("value", 1);
                if (value <= 0) {
                    warn(logger, "[battlepass] 任务 " + taskId
                        + " 的 fixed 增量 value 非法: " + value + "，已回退为 1");
                    value = 1;
                }
                yield new FixedIncrementStrategy(value);
            }
            case "payload_value" -> parsePayloadValue(section, logger, taskId);
            default -> {
                warn(logger, "[battlepass] 任务 " + taskId
                    + " 包含未知增量策略类型: " + type
                    + "，支持列表: " + SUPPORTED_TYPES + "；已回退为 fixed 1");
                yield new FixedIncrementStrategy(1);
            }
        };
    }

    private static IncrementStrategy parsePayloadValue(
        ConfigurationSection section, Logger logger, String taskId
    ) {
        double scale = section.getDouble("scale", 1.0);
        int max = section.getInt("max-per-event", 0);
        int min = section.getInt("min-per-event", 0);
        if (scale < 0) {
            warn(logger, "[battlepass] 任务 " + taskId
                + " 的 payload_value scale 非法: " + scale + "，已回退为 1.0");
            scale = 1.0;
        }
        if (max < 0) {
            warn(logger, "[battlepass] 任务 " + taskId
                + " 的 payload_value max-per-event 非法: " + max + "，已回退为 0");
            max = 0;
        }
        if (max > 0 && min > max) {
            warn(logger, "[battlepass] 任务 " + taskId
                + " 的 payload_value.min-per-event (" + min
                + ") 大于 max-per-event (" + max + ")，已夹到 max-per-event");
            min = max;
        }
        if (min < 0) {
            warn(logger, "[battlepass] 任务 " + taskId
                + " 的 payload_value min-per-event 非法: " + min + "，已回退为 0");
            min = 0;
        }
        return new PayloadValueStrategy(
            section.getString("payload-key", ""), max, min, scale);
    }

    private static void warn(Logger logger, String message) {
        if (logger != null) logger.warning(message);
    }
}
