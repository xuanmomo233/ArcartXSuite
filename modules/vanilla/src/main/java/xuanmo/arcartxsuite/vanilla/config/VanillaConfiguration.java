package xuanmo.arcartxsuite.vanilla.config;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Vanilla 模块配置。
 */
public final class VanillaConfiguration {

    private final Crafting crafting;
    private final Furnace furnace;

    private VanillaConfiguration(Crafting crafting, Furnace furnace) {
        this.crafting = crafting;
        this.furnace = furnace;
    }

    public Crafting crafting() {
        return crafting;
    }

    public Furnace furnace() {
        return furnace;
    }

    public static VanillaConfiguration load(YamlConfiguration yaml) {
        Crafting crafting = new Crafting(
            yaml.getBoolean("crafting.enabled", true),
            yaml.getString("crafting.ui-id", "vanilla_crafting"),
            yaml.getBoolean("crafting.overwrite-ui-file", false)
        );
        Furnace furnace = new Furnace(
            yaml.getBoolean("furnace.enabled", true),
            yaml.getString("furnace.ui-id", "vanilla_furnace"),
            yaml.getBoolean("furnace.overwrite-ui-file", false),
            yaml.getInt("furnace.push-interval-ticks", 2)
        );
        return new VanillaConfiguration(crafting, furnace);
    }

    public record Crafting(boolean enabled, String uiId, boolean overwriteUiFile) {
    }

    public record Furnace(boolean enabled, String uiId, boolean overwriteUiFile, int pushIntervalTicks) {
    }
}
