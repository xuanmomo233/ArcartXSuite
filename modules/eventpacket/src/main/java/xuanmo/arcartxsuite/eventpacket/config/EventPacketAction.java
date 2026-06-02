package xuanmo.arcartxsuite.eventpacket.config;

import java.util.LinkedHashMap;
import java.util.Map;

public record EventPacketAction(
    String type,
    Map<String, Object> values
) {
    public EventPacketAction {
        values = values == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(values));
    }

    public String string(String key, String fallback) {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

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

    public Object object(String key) {
        return values.get(key);
    }
}
