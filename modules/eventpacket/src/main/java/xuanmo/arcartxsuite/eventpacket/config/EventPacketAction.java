package xuanmo.arcartxsuite.eventpacket.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 事件包规则中单个动作的配置记录。
 * <p>
 * 每个动作由类型标识和一组键值参数组成，
 * 由 {@code EventPacketDispatchService} 在触发时解析执行。
 *
 * @param type   动作类型标识（如 {@code message}、{@code command} 等）
 * @param values 动作参数键值表，不可变；{@code null} 时视为空表
 */
public record EventPacketAction(
    String type,
    Map<String, Object> values
) {
    public EventPacketAction {
        values = values == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(values));
    }

    /**
     * 以字符串形式读取参数值，空值或空白时返回回退值。
     *
     * @param key      参数键
     * @param fallback 缺省回退值
     * @return 规范化后的字符串值
     */
    public String string(String key, String fallback) {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    /**
     * 以布尔形式读取参数值，支持 true/false/yes/no/on/off 文本判断。
     *
     * @param key      参数键
     * @param fallback 缺省回退值
     * @return 解析后的布尔值
     */
    public boolean bool(String key, boolean fallback) {
        Object value = values.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            if ("true".equalsIgnoreCase(stringValue) || "yes".equalsIgnoreCase(stringValue) || "on".equalsIgnoreCase(stringValue)) {
                return true;
            }
            if ("false".equalsIgnoreCase(stringValue) || "no".equalsIgnoreCase(stringValue) || "off".equalsIgnoreCase(stringValue)) {
                return false;
            }
        }
        return fallback;
    }

    /**
     * 以原始对象形式读取参数值。
     *
     * @param key 参数键
     * @return 原始值，不存在时返回 {@code null}
     */
    public Object object(String key) {
        return values.get(key);
    }
}
