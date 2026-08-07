package xuanmo.arcartxsuite.entitytracker.target.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 实体目标快照，属于 entitytracker 模块的目标 HUD 子系统。
 * <p>
 * 持有玩家当前攻击目标的完整渲染状态（血量、距离、类型、位置等），
 * 支持模板渲染、UI 数据包转换和占位符解析。
 *
 * @param title             标题文本
 * @param subtitle          副标题文本
 * @param health            当前血量
 * @param maxHealth         最大血量
 * @param healthText        血量显示文本
 * @param maxHealthText     最大血量显示文本
 * @param healthPercent     血量百分比
 * @param healthPercentText 血量百分比显示文本
 * @param progress          血量进度（0-1）
 * @param distance          距离（方块）
 * @param distanceText      距离显示文本
 * @param displayName       目标显示名称
 * @param entityUuid        目标实体 UUID
 * @param world             所在世界名称
 * @param x                 坐标 X
 * @param y                 坐标 Y
 * @param z                 坐标 Z
 * @param entityType        实体类型标识
 * @param entityTypeName    实体类型显示名称
 * @param mythicMobId       MythicMob ID（非 MythicMob 时为空）
 * @param playerTarget      目标是否为玩家
 * @param lastHitAgoMs      距上次攻击的毫秒数
 * @param timeoutMs         目标超时毫秒数
 */
public record EntityTargetSnapshot(
    String title,
    String subtitle,
    double health,
    double maxHealth,
    String healthText,
    String maxHealthText,
    double healthPercent,
    String healthPercentText,
    double progress,
    int distance,
    String distanceText,
    String displayName,
    String entityUuid,
    String world,
    int x,
    int y,
    int z,
    String entityType,
    String entityTypeName,
    String mythicMobId,
    boolean playerTarget,
    long lastHitAgoMs,
    long timeoutMs
) {

    /**
     * 使用标题和副标题模板渲染后返回新的快照。
     *
     * @param titleTemplate    标题模板
     * @param subtitleTemplate 副标题模板
     * @return 渲染后的新快照
     */
    public EntityTargetSnapshot withRenderedTexts(String titleTemplate, String subtitleTemplate) {
        Map<String, String> values = placeholderValues();
        return new EntityTargetSnapshot(
            render(titleTemplate, values),
            render(subtitleTemplate, values),
            health,
            maxHealth,
            healthText,
            maxHealthText,
            healthPercent,
            healthPercentText,
            progress,
            distance,
            distanceText,
            displayName,
            entityUuid,
            world,
            x,
            y,
            z,
            entityType,
            entityTypeName,
            mythicMobId,
            playerTarget,
            lastHitAgoMs,
            timeoutMs
        );
    }

    /**
     * 转换为 UI 数据包 Map。
     *
     * @return 包含所有字段的 Map
     */
    public Map<String, Object> toPacket() {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("title", title);
        packet.put("subtitle", subtitle);
        packet.put("health", health);
        packet.put("maxHealth", maxHealth);
        packet.put("healthText", healthText);
        packet.put("maxHealthText", maxHealthText);
        packet.put("healthPercent", healthPercent);
        packet.put("healthPercentText", healthPercentText);
        packet.put("progress", progress);
        packet.put("distance", distance);
        packet.put("distanceText", distanceText);
        packet.put("displayName", displayName);
        packet.put("entityUuid", entityUuid);
        packet.put("world", world);
        packet.put("x", x);
        packet.put("y", y);
        packet.put("z", z);
        packet.put("entityType", entityType);
        packet.put("entityTypeName", entityTypeName);
        packet.put("mythicMobId", mythicMobId);
        packet.put("isPlayerTarget", playerTarget);
        packet.put("lastHitAgoMs", lastHitAgoMs);
        packet.put("timeoutMs", timeoutMs);
        return packet;
    }

    /**
     * 解析占位符字段名对应的值。
     *
     * @param field 字段名
     * @return 解析结果，无法识别时返回 {@code null}
     */
    public String resolvePlaceholder(String field) {
        if (field == null) {
            return null;
        }
        String key = field.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "title" -> title;
            case "subtitle" -> subtitle;
            case "health_raw" -> Double.toString(health);
            case "max_health_raw" -> Double.toString(maxHealth);
            case "health_percent_raw" -> Double.toString(healthPercent);
            case "progress" -> Double.toString(progress);
            case "distance_raw" -> Integer.toString(distance);
            default -> placeholderValues().get(key);
        };
    }

    private String render(String template, Map<String, String> values) {
        String rendered = template == null ? "" : template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return rendered.replace("\\n", "\n");
    }

    private Map<String, String> placeholderValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("display_name", displayName);
        values.put("health", healthText);
        values.put("health_text", healthText);
        values.put("max_health", maxHealthText);
        values.put("max_health_text", maxHealthText);
        values.put("health_percent", healthPercentText);
        values.put("health_percent_text", healthPercentText);
        values.put("progress", Double.toString(progress));
        values.put("progress_text", Double.toString(progress));
        values.put("distance", Integer.toString(distance));
        values.put("distance_text", distanceText);
        values.put("entity_uuid", entityUuid);
        values.put("entity_type", entityType);
        values.put("entity_type_name", entityTypeName);
        values.put("mythic_mob_id", mythicMobId);
        values.put("is_player_target", Boolean.toString(playerTarget));
        values.put("world", world);
        values.put("x", Integer.toString(x));
        values.put("y", Integer.toString(y));
        values.put("z", Integer.toString(z));
        values.put("last_hit_ago_ms", Long.toString(lastHitAgoMs));
        values.put("timeout_ms", Long.toString(timeoutMs));
        return values;
    }
}

