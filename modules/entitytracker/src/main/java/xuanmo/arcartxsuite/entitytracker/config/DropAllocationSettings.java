package xuanmo.arcartxsuite.entitytracker.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public record DropAllocationSettings(
    boolean enabled,
    String defaultMode,
    int allocationTimeoutSeconds,
    DkpSettings dkp,
    RollSettings roll,
    PrioritySettings priority
) {

    public static DropAllocationSettings disabled() {
        return new DropAllocationSettings(
            false, "roll", 60,
            DkpSettings.disabled(), RollSettings.defaults(), PrioritySettings.defaults()
        );
    }

    public static DropAllocationSettings load(ConfigurationSection section) {
        if (section == null) {
            return disabled();
        }
        return new DropAllocationSettings(
            section.getBoolean("enabled", false),
            section.getString("default-mode", "roll").toLowerCase(Locale.ROOT),
            Math.max(10, section.getInt("allocation-timeout", 60)),
            DkpSettings.load(section.getConfigurationSection("dkp")),
            RollSettings.load(section.getConfigurationSection("roll")),
            PrioritySettings.load(section.getConfigurationSection("priority"))
        );
    }

    public record DkpSettings(
        boolean enabled,
        int baseEarnPoints,
        Map<Integer, Integer> rankBonusPoints
    ) {
        public static DkpSettings disabled() {
            return new DkpSettings(false, 10, Map.of());
        }

        public static DkpSettings load(ConfigurationSection section) {
            if (section == null || !section.getBoolean("enabled", false)) {
                return disabled();
            }
            Map<Integer, Integer> rankBonus = new LinkedHashMap<>();
            ConfigurationSection bonusSection = section.getConfigurationSection("rank-bonus-points");
            if (bonusSection != null) {
                for (String key : bonusSection.getKeys(false)) {
                    try {
                        rankBonus.put(Integer.parseInt(key), bonusSection.getInt(key));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return new DkpSettings(true, section.getInt("base-earn-points", 10), Map.copyOf(rankBonus));
        }

        public int bonusForRank(int rank) {
            return rankBonusPoints.getOrDefault(rank, 0);
        }
    }

    public record RollSettings(
        boolean enabled,
        int timeoutSeconds,
        int rollMin,
        int rollMax,
        boolean allowPass
    ) {
        public static RollSettings defaults() {
            return new RollSettings(true, 30, 1, 100, true);
        }

        public static RollSettings load(ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            int min = 1;
            int max = 100;
            String range = section.getString("roll-range", "1-100");
            if (range != null && range.contains("-")) {
                String[] parts = range.split("-", 2);
                try {
                    min = Integer.parseInt(parts[0].trim());
                    max = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ignored) {
                }
            }
            return new RollSettings(
                section.getBoolean("enabled", true),
                Math.max(5, section.getInt("timeout-seconds", 30)),
                min,
                max,
                section.getBoolean("allow-pass", true)
            );
        }
    }

    public record PrioritySettings(
        boolean enabled,
        Map<String, Integer> classPriority,
        Map<String, Integer> qualityPriority
    ) {
        public static PrioritySettings defaults() {
            return new PrioritySettings(
                true,
                Map.of("tank", 1, "healer", 2, "dps", 3),
                Map.of("legendary", 1, "epic", 2, "rare", 3, "common", 4)
            );
        }

        public static PrioritySettings load(ConfigurationSection section) {
            if (section == null || !section.getBoolean("enabled", true)) {
                return new PrioritySettings(false, Map.of(), Map.of());
            }
            return new PrioritySettings(
                true,
                readIntMap(section.getConfigurationSection("class-priority")),
                readIntMap(section.getConfigurationSection("quality-priority"))
            );
        }

        private static Map<String, Integer> readIntMap(ConfigurationSection section) {
            if (section == null) {
                return Map.of();
            }
            Map<String, Integer> map = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) {
                map.put(key.toLowerCase(Locale.ROOT), section.getInt(key));
            }
            return Map.copyOf(map);
        }
    }
}
