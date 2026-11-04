package xuanmo.arcartxsuite.questgps.chemdah;

import ink.ptms.chemdah.core.quest.QuestContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

/** Converts only the owned view component across Chemdah's relocated configuration boundary. */
public final class ChemdahConfigAccessor {
    private ChemdahConfigAccessor() {}

    public static Object nativeSection(QuestContainer container, String path) {
        Object config = container.getConfig();
        if (config == null) return null;
        try {
            return config.getClass().getMethod("getConfigurationSection", String.class).invoke(config, path);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法读取 Chemdah 展示组件: " + container.getId(), e);
        }
    }

    public static ConfigurationSection fromRaw(Object config) {
        if (config == null) return null;
        if (config instanceof ConfigurationSection section) return section;
        try {
            Object raw = config.getClass().getMethod("getValues", boolean.class).invoke(config, true);
            if (!(raw instanceof Map<?, ?> values)) return null;
            MemoryConfiguration copy = new MemoryConfiguration();
            for (var entry : values.entrySet()) {
                if (!(entry.getKey() instanceof String key) || entry.getValue() == null) continue;
                Object value = entry.getValue();
                try {
                    value.getClass().getMethod("getValues", boolean.class);
                    continue;
                } catch (NoSuchMethodException ignored) {
                    copy.set(key, value);
                }
            }
            return copy;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Chemdah 展示配置无法解析", e);
        }
    }

    public static List<String> normalizeMultiline(Object raw) {
        if (raw == null) return List.of();
        List<String> lines = new ArrayList<>();
        if (raw instanceof Iterable<?> values) {
            for (Object value : values) lines.addAll(normalizeMultiline(value));
        } else {
            for (String line : String.valueOf(raw).split("\\R")) {
                if (!line.trim().isEmpty()) lines.add(line.trim());
            }
        }
        return List.copyOf(lines);
    }
}
