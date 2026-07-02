package xuanmo.arcartxsuite.questgps.chemdah;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

/**
 * 统一读取 Chemdah Template / Task 的 ConfigurationSection。
 */
public final class ChemdahConfigAccessor {

    private ChemdahConfigAccessor() {
    }

    public static ConfigurationSection section(Object holder) {
        if (holder == null) {
            return null;
        }
        try {
            Method method = holder.getClass().getMethod("getConfig");
            Object config = method.invoke(holder);
            if (config instanceof ConfigurationSection section) {
                return section;
            }
            if (config == null) {
                return null;
            }
            Method valuesMethod = config.getClass().getMethod("getValues", boolean.class);
            Object values = valuesMethod.invoke(config, true);
            if (!(values instanceof Map<?, ?> map)) {
                return null;
            }
            MemoryConfiguration memory = new MemoryConfiguration();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                if (!(key instanceof String path) || value == null) {
                    continue;
                }
                Class<?> valueType = value.getClass();
                if (ConfigurationSection.class.isAssignableFrom(valueType)) {
                    continue;
                }
                try {
                    valueType.getMethod("getValues", boolean.class);
                    continue;
                } catch (NoSuchMethodException ignored) {
                }
                memory.set(path, value);
            }
            return memory;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public static String readString(Object holder, String path, String fallback) {
        ConfigurationSection section = section(holder);
        if (section == null || path == null || path.isBlank()) {
            return fallback;
        }
        String value = section.getString(path);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    public static List<String> readMultiline(Object holder, String path) {
        ConfigurationSection section = section(holder);
        if (section == null || path == null || path.isBlank()) {
            return List.of();
        }
        return normalizeMultiline(section.get(path));
    }

    public static List<String> normalizeMultiline(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof Iterable<?> iterable) {
            List<String> lines = new ArrayList<>();
            for (Object entry : iterable) {
                lines.addAll(normalizeMultiline(entry));
            }
            return List.copyOf(lines);
        }
        String value = String.valueOf(raw);
        List<String> lines = new ArrayList<>();
        for (String line : value.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return List.copyOf(lines);
    }

    public static String pickString(PresentationSource source, String chemdahValue, String overlayValue, String fallback) {
        String overlay = overlayValue == null || overlayValue.isBlank() ? "" : overlayValue.trim();
        String chemdah = chemdahValue == null || chemdahValue.isBlank() ? "" : chemdahValue.trim();
        if (source == PresentationSource.OVERLAY) {
            return overlay.isEmpty() ? fallback : overlay;
        }
        return chemdah.isEmpty() ? fallback : chemdah;
    }

    public static List<String> pickLines(PresentationSource source, List<String> chemdahLines, List<String> overlayLines, List<String> fallback) {
        if (source == PresentationSource.OVERLAY) {
            return overlayLines.isEmpty() ? fallback : overlayLines;
        }
        return chemdahLines.isEmpty() ? fallback : chemdahLines;
    }
}
