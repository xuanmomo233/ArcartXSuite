package xuanmo.arcartxsuite.entitytracker.target.service;

import java.util.LinkedHashMap;
import java.util.Map;

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

