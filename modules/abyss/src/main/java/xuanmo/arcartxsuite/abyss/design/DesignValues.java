package xuanmo.arcartxsuite.abyss.design;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;

/** Detached YAML snapshots. Only schema property names are aliases; user IDs are never normalized. */
public final class DesignValues {
    private DesignValues() { }
    private static final Set<String> PROPERTIES = new HashSet<>();
    static {
        for (var kind : DesignObject.Kind.values())
            for (var field : DesignObject.schema(kind)) PROPERTIES.add(field.name());
        PROPERTIES.addAll(List.of("mob-id", "on-prespawn", "rest-seconds", "wave-rest",
            "boss-room", "room-count", "trigger-after", "manual-clear", "on-wave"));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> copy(Map<String, ?> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key, copyValue(value)));
        return result;
    }

    public static Object copyValue(Object value) {
        if (value instanceof ConfigurationSection section) return copy(section.getValues(false));
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), copyValue(item)));
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) result.add(copyValue(item));
            return result;
        }
        return value;
    }

    public static Map<String, Object> properties(Map<String, ?> values) {
        Map<String, Object> result = copy(values);
        for (String key : new ArrayList<>(result.keySet())) {
            String canonical = key.replace('_', '-');
            if (!key.equals(canonical) && PROPERTIES.contains(canonical)) {
                if (!result.containsKey(canonical)) result.put(canonical, result.get(key));
                result.remove(key);
            }
        }
        return result;
    }

    public static String flow(Object value) {
        return new com.google.gson.GsonBuilder().disableHtmlEscaping().create().toJson(copyValue(value));
    }
}
