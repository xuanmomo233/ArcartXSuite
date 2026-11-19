package xuanmo.arcartxsuite.vanilla.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.util.EnumMap;
import java.util.Map;
import xuanmo.arcartxsuite.vanilla.recipe.WorkshopType;
import xuanmo.arcartxsuite.vanilla.service.InventorySorter;

/**
 * Vanilla 模块配置。
 */
public final class VanillaConfiguration {

    private final Ui ui;
    private final Crafting crafting;
    private final Furnace furnace;
    private final InventorySorter.Mode sortMode;
    private final Map<WorkshopType, Boolean> enabled = new EnumMap<>(WorkshopType.class);
    private final Map<WorkshopType, String> uiIds = new EnumMap<>(WorkshopType.class);
    private final Map<WorkshopType, Integer> intervals = new EnumMap<>(WorkshopType.class);

    public boolean enabled(WorkshopType type) { return enabled.getOrDefault(type, true); }
    public String uiId(WorkshopType type) { return uiIds.getOrDefault(type, type.uiId()); }
    public int interval(WorkshopType type) { return intervals.getOrDefault(type, 2); }

    private VanillaConfiguration(Ui ui, Crafting crafting, Furnace furnace, InventorySorter.Mode sortMode) {
        this.ui = ui;
        this.crafting = crafting;
        this.furnace = furnace;
        this.sortMode = sortMode;
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

    public InventorySorter.Mode sortMode() {
        return sortMode;
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
        VanillaConfiguration configuration = new VanillaConfiguration(ui, crafting, furnace,
            InventorySorter.Mode.parse(yaml.getString("inventory-sort.mode", "type")));
        for (WorkshopType type : WorkshopType.values()) {
            configuration.enabled.put(type, yaml.getBoolean(type.configKey() + ".enabled", true));
            configuration.uiIds.put(type, yaml.getString("ui." + type.configKey() + "-ui-id", type.uiId()));
            configuration.intervals.put(type, Math.max(1, Math.min(100, yaml.getInt(type.configKey() + ".push-interval-ticks", 2))));
        }
        return configuration;
    }

    public record Ui(String craftingUiId, String furnaceUiId, boolean overwriteUiFiles) {
    }

    public record Crafting(boolean enabled) {
    }

    public record Furnace(boolean enabled, int pushIntervalTicks) {
    }
}
