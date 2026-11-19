package xuanmo.arcartxsuite.essentials.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;

public record EssentialsFeatures(
    Set<String> disabledActions,
    Tpa tpa,
    Unstuck unstuck,
    boolean offlineBans,
    int historyPageSize
) {
    public static EssentialsFeatures load(ConfigurationSection root) {
        ConfigurationSection tpa = root.getConfigurationSection("tpa");
        ConfigurationSection unstuck = root.getConfigurationSection("unstuck");
        return new EssentialsFeatures(
            root.getStringList("disabled-actions").stream()
                .map(action -> action.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet()),
            new Tpa(flag(tpa, "enabled", true), number(tpa, "cooldown-seconds", 5, 0, 3600),
                number(tpa, "max-incoming", 5, 1, 50), number(tpa, "max-outgoing", 5, 1, 50)),
            new Unstuck(flag(unstuck, "enabled", true), number(unstuck, "cooldown-seconds", 120, 0, 86400),
                number(unstuck, "warmup-seconds", 3, 0, 60), number(unstuck, "combat-seconds", 15, 0, 600),
                number(unstuck, "search-radius", 4, 1, 8),
                number(unstuck, "vertical-range", 4, 0, 8), flag(unstuck, "only-when-stuck", true),
                worlds(unstuck, "allowed-worlds"), worlds(unstuck, "blocked-worlds")),
            root.getBoolean("moderation.allow-offline-bans", true),
            Math.max(1, Math.min(25, root.getInt("moderation.history-page-size", 10)))
        );
    }

    public boolean actionEnabled(String action) {
        if (Set.of("help", "status", "reload").contains(action)) return true;
        if (!tpa.enabled() && Set.of("tpa", "tpahere", "tpaccept", "tpdeny", "tptoggle", "tpcancel").contains(action)) return false;
        return !disabledActions.contains(action) && (!action.equals("unstuck") || unstuck.enabled());
    }


    private static int number(ConfigurationSection section, String key, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, section == null ? fallback : section.getInt(key, fallback)));
    }

    private static boolean flag(ConfigurationSection section, String key, boolean fallback) {
        return section == null ? fallback : section.getBoolean(key, fallback);
    }

    private static Set<String> worlds(ConfigurationSection section, String key) {
        List<String> values = section == null ? List.of() : section.getStringList(key);
        return values.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public record Tpa(boolean enabled, int cooldownSeconds, int maxIncoming, int maxOutgoing) {}
    public record Unstuck(boolean enabled, int cooldownSeconds, int warmupSeconds, int combatSeconds, int radius, int verticalRange,
                          boolean onlyWhenStuck, Set<String> allowedWorlds, Set<String> blockedWorlds) {
        public boolean allowsWorld(String world) {
            String key = world.toLowerCase(Locale.ROOT);
            return !blockedWorlds.contains(key) && (allowedWorlds.isEmpty() || allowedWorlds.contains(key));
        }
    }
}
