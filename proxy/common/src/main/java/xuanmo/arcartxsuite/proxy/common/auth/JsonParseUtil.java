package xuanmo.arcartxsuite.proxy.common.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.logging.Logger;

/**
 * JSON 字段提取工具：基于 Gson 解析，替代手写字符串扫描。
 * <p>
 * 统一 {@link MixedYggdrasilProxy} 和 {@link AuthlibInjectorManager} 的 JSON 解析逻辑，
 * 消除原 {@code extractJsonString} 手写实现的边缘 case 隐患：
 * <ul>
 *   <li>同名字段误匹配（嵌套对象/数组中出现相同 key）</li>
 *   <li>非字符串值跳过（数字/布尔字段后误匹配到下一个引号）</li>
 *   <li>unicode 转义未处理（backslash-u XXXX）</li>
 * </ul>
 *
 * @since 1.5.1
 */
final class JsonParseUtil {

    private JsonParseUtil() {}

    /**
     * 从 JSON 字符串中提取顶层字符串字段。
     * <p>
     * 解析失败时静默返回 null；若 debug=true 且 logger 非空，则输出警告日志（截断前 200 字符），
     * 便于排查 Mojang/LittleSkin/authlib-injector 响应结构变更。
     *
     * @param json   JSON 字符串
     * @param key    目标字段名
     * @param logger 日志记录器（null 表示不输出日志）
     * @param debug  是否输出 debug 级别日志
     * @return 字段值；解析失败或字段不存在时返回 null
     */
    static String getStringField(String json, String key, Logger logger, boolean debug) {
        if (json == null || json.isBlank() || key == null || key.isBlank()) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                logFailure(logger, debug, key, "响应非 JSON 对象", json);
                return null;
            }
            JsonObject obj = root.getAsJsonObject();
            JsonElement element = obj.get(key);
            if (element == null || element.isJsonNull()) {
                logFailure(logger, debug, key, "字段不存在", json);
                return null;
            }
            // 优先用 getAsString()，兼容字符串值；若值为数字/布尔也尝试转字符串
            try {
                return element.getAsString();
            } catch (UnsupportedOperationException e) {
                // 非字符串类型（数字/布尔），用 toString 兜底
                return element.toString();
            }
        } catch (Exception e) {
            logFailure(logger, debug, key, "解析异常: " + e.getMessage(), json);
            return null;
        }
    }

    private static void logFailure(Logger logger, boolean debug, String key, String reason, String json) {
        if (logger != null && debug) {
            String preview = json.length() > 200 ? json.substring(0, 200) + "..." : json;
            logger.warning("[JsonParseUtil] 提取字段 \"" + key + "\" 失败: " + reason + " | 响应片段: " + preview);
        }
    }
}
