package xuanmo.arcartxsuite.entitytracker.config;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

public record CrossServerRankingSettings(
    boolean enabled,
    int updateIntervalSeconds,
    List<String> rankingTypes,
    int maxEntries
) {

    public static final String TYPE_BEST_DAMAGE = "best_damage";
    public static final String TYPE_BOSS_DAMAGE = "boss_damage";
    public static final String TYPE_KILLS = "kills";
    public static final String TYPE_PARTICIPATE = "participate";
    public static final String TYPE_SERVER = "server";

    public static CrossServerRankingSettings disabled() {
        return new CrossServerRankingSettings(false, 60, List.of(TYPE_BEST_DAMAGE), 50);
    }

    public static CrossServerRankingSettings load(ConfigurationSection section) {
        if (section == null) {
            return disabled();
        }
        List<String> types = section.getStringList("ranking-types");
        if (types.isEmpty()) {
            types = List.of(TYPE_BEST_DAMAGE);
        }
        return new CrossServerRankingSettings(
            section.getBoolean("enabled", false),
            Math.max(15, section.getInt("update-interval", 60)),
            List.copyOf(new ArrayList<>(types)),
            Math.max(1, section.getInt("max-entries", 50))
        );
    }
}
