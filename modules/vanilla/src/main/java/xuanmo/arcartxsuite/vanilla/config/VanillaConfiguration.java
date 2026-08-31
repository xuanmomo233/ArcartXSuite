package xuanmo.arcartxsuite.vanilla.config;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Vanilla 模块配置。
 */
public final class VanillaConfiguration {

    private final Ui ui;
    private final Crafting crafting;
    private final Furnace furnace;

    private VanillaConfiguration(Ui ui, Crafting crafting, Furnace furnace) {
        this.ui = ui;
        this.crafting = crafting;
        this.furnace = furnace;
    }

    public Ui ui() {
        return ui;
    }

    public Crafting crafting() {
        return crafting;
    }

    public Furnace furnace() {
        return furnace;
    }

    public static VanillaConfiguration load(YamlConfiguration yaml) {
        Ui ui = new Ui(
            yaml.getString("ui.crafting-ui-id", "AXS:vanilla_crafting"),
            yaml.getString("ui.furnace-ui-id", "AXS:vanilla_furnace"),
            yaml.getBoolean("ui.overwrite-ui-files", false)
        );
        Crafting crafting = new Crafting(
            yaml.getBoolean("crafting.enabled", true)
        );
        Furnace furnace = new Furnace(
            yaml.getBoolean("furnace.enabled", true),
            yaml.getInt("furnace.push-interval-ticks", 2)
        );
        return new VanillaConfiguration(ui, crafting, furnace);
    }

    public record Ui(String craftingUiId, String furnaceUiId, boolean overwriteUiFiles) {
    }

    public record Crafting(boolean enabled) {
    }

    public record Furnace(boolean enabled, int pushIntervalTicks) {
    }
}
