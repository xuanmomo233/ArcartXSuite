package xuanmo.arcartxsuite.vanilla.backpack.config;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/** Configured names refer to animations already supplied by the player's ArcartX resources. */
public record BackpackPresentationConfig(
    boolean enabled, int transitionMs, int blendMs, Action opening, String idle,
    int minRandomMs, int maxRandomMs, List<WeightedAction> randomActions
) {
    static BackpackPresentationConfig load(ConfigurationSection section) {
        if (section == null) {
            return new BackpackPresentationConfig(false, 0, 100, null, "", 6000, 12000, List.of());
        }
        int minRandomMs = bounded(section, "random.min-interval-ms", 6000, 250, 120000);
        int maxRandomMs = bounded(section, "random.max-interval-ms", 12000, minRandomMs, 120000);
        var randomActions = new ArrayList<WeightedAction>();
        var pool = section.getConfigurationSection("random.actions");
        if (pool != null) {
            for (String key : pool.getKeys(false)) {
                var entry = pool.getConfigurationSection(key);
                Action action = readAction(entry, 650);
                if (action != null) {
                    int weight = bounded(entry, "weight", 1, 0, 10000);
                    if (weight > 0) randomActions.add(new WeightedAction(action, weight));
                }
            }
        }
        return new BackpackPresentationConfig(
            section.getBoolean("enabled", false),
            bounded(section, "transition-ms", 220, 0, 3000),
            bounded(section, "blend-ms", 100, 0, 2000),
            readAction(section.getConfigurationSection("open"), 450),
            section.getString("idle.animation", "").strip(),
            minRandomMs, maxRandomMs, List.copyOf(randomActions)
        );
    }

    public int activeTransitionMs() { return enabled ? transitionMs : 0; }

    public boolean hasAnimations() {
        return enabled && (opening != null || !idle.isEmpty() || !randomActions.isEmpty());
    }

    private static Action readAction(ConfigurationSection section, int defaultDurationMs) {
        if (section == null) return null;
        String name = section.getString("animation", "").strip();
        return name.isEmpty() ? null : new Action(name, bounded(section, "duration-ms", defaultDurationMs, 50, 60000));
    }

    private static int bounded(ConfigurationSection section, String path, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, section.getInt(path, fallback)));
    }

    public record Action(String animation, int durationMs) {}

    public record WeightedAction(Action action, int weight) {}
}
