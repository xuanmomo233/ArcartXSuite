package xuanmo.arcartxsuite.battlepass.increment;

import org.bukkit.configuration.ConfigurationSection;

public final class IncrementStrategyParser {

    private IncrementStrategyParser() {}

    public static IncrementStrategy parse(ConfigurationSection section) {
        if (section == null) return new FixedIncrementStrategy(1);
        String type = section.getString("type", "fixed");
        return switch (type) {
            case "fixed" -> new FixedIncrementStrategy(section.getInt("value", 1));
            case "payload_value" -> new PayloadValueStrategy(
                section.getString("payload-key", ""),
                section.getInt("max-per-event", 0),
                section.getDouble("scale", 1.0)
            );
            default -> new FixedIncrementStrategy(1);
        };
    }
}
