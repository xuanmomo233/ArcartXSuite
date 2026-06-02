package xuanmo.arcartxsuite.combateffect.combo;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

public record ComboTrackerConfiguration(
    boolean enabled,
    ComboSource source,
    long timeoutMs,
    Set<String> chronosGroups,
    boolean syncVariable,
    String variableName,
    boolean perTarget,
    boolean debug
) {

    public enum ComboSource {
        AUTO, CHRONOS, BUKKIT;

        public static ComboSource from(String value) {
            if (value == null) return AUTO;
            return switch (value.trim().toLowerCase()) {
                case "chronos" -> CHRONOS;
                case "bukkit" -> BUKKIT;
                default -> AUTO;
            };
        }
    }

    public static ComboTrackerConfiguration load(ConfigurationSection section) {
        if (section == null) {
            return new ComboTrackerConfiguration(false, ComboSource.AUTO, 2000L, Set.of(), false, "combo_count", false, false);
        }
        boolean enabled = section.getBoolean("enabled", false);
        ComboSource source = ComboSource.from(section.getString("source", "auto"));
        long timeoutMs = Math.max(200L, section.getLong("timeout", 2000L));
        Set<String> chronosGroups = new HashSet<>(section.getStringList("chronos-groups"));
        if (chronosGroups.isEmpty()) {
            chronosGroups.add("攻击");
        }
        boolean syncVariable = section.getBoolean("sync-variable", false);
        String variableName = section.getString("variable-name", "combo_count");
        boolean perTarget = section.getBoolean("per-target", false);
        boolean debug = section.getBoolean("debug", false);
        return new ComboTrackerConfiguration(enabled, source, timeoutMs, chronosGroups, syncVariable, variableName, perTarget, debug);
    }
}
