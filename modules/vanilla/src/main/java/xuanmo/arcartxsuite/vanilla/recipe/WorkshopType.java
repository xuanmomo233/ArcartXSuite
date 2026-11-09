package xuanmo.arcartxsuite.vanilla.recipe;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.*;

/** Native slots and recipes belong to one exact workstation type. */
public enum WorkshopType {
    CRAFTING("crafting", "工作台", InventoryType.WORKBENCH, 10, 0, 1, 9),
    FURNACE("furnace", "熔炉", InventoryType.FURNACE, 3, 2, 0, 1),
    BLAST_FURNACE("blast_furnace", "高炉", InventoryType.BLAST_FURNACE, 3, 2, 0, 1),
    SMOKER("smoker", "烟熏炉", InventoryType.SMOKER, 3, 2, 0, 1),
    STONECUTTER("stonecutter", "切石机", InventoryType.STONECUTTER, 2, 1, 0, 1),
    SMITHING("smithing", "锻造台", InventoryType.SMITHING, 4, 3, 0, 3),
    BREWING("brewing", "酿造台", InventoryType.BREWING, 5, -1, 0, 4),
    MERCHANT("merchant", "村民交易", InventoryType.MERCHANT, 3, 2, 0, 2),
    ANVIL("anvil", "铁砧", InventoryType.ANVIL, 3, 2, 0, 2),
    ENCHANTING("enchanting", "附魔台", InventoryType.ENCHANTING, 2, -1, 0, 2),
    GRINDSTONE("grindstone", "砂轮", InventoryType.GRINDSTONE, 3, 2, 0, 2),
    LOOM("loom", "织布机", InventoryType.LOOM, 4, 3, 0, 3),
    CARTOGRAPHY("cartography", "制图台", InventoryType.CARTOGRAPHY, 3, 2, 0, 2),
    BEACON("beacon", "信标", InventoryType.BEACON, 1, -1, 0, 1),
    HOPPER("hopper", "漏斗", InventoryType.HOPPER, 5, -1, 0, 5),
    DISPENSER("dispenser", "发射器与投掷器", InventoryType.DISPENSER, 9, -1, 0, 9),
    CONTAINER("container", "箱柜与末影箱", InventoryType.CHEST, 27, -1, 0, 27),
    SHULKER_BOX("shulker_box", "潜影盒", InventoryType.SHULKER_BOX, 27, -1, 0, 27);

    private final String key, title;
    private final InventoryType inventoryType;
    private final int size, resultSlot, firstInput, inputCount;

    WorkshopType(String key, String title, InventoryType inventoryType, int size, int resultSlot, int firstInput, int inputCount) {
        this.key = key; this.title = title; this.inventoryType = inventoryType;
        this.size = size; this.resultSlot = resultSlot; this.firstInput = firstInput; this.inputCount = inputCount;
    }
    public String key() { return key; }
    public String configKey() { return key.replace('_', '-'); }
    public String title() { return title; }
    public String uiId() { return "AXS:vanilla_" + key; }
    public String resource() { return "arcartx/ui/vanilla_" + key + ".yml"; }
    public String file() { return "ui/vanilla_" + key + "_v2.yml"; }
    public int size() { return size; }
    public int resultSlot() { return resultSlot; }
    public int inputCount() { return inputCount; }
    public int inputCount(InventoryView view) { return this == CONTAINER ? view.getTopInventory().getSize() : inputCount; }
    public int inputSlot(int index) { return firstInput + index; }
    public boolean cooking() { return this == FURNACE || this == BLAST_FURNACE || this == SMOKER; }
    public boolean processing() { return cooking() || this == BREWING; }
    public boolean persistent() { return processing() || storage(); }
    public boolean equipment() { return this == ANVIL || this == ENCHANTING; }
    public boolean artisan() { return this == GRINDSTONE || this == LOOM || this == CARTOGRAPHY; }
    public boolean storage() { return this == HOPPER || this == DISPENSER || this == CONTAINER || this == SHULKER_BOX; }
    public boolean utility() { return this == BEACON || storage(); }
    public boolean matches(InventoryView view) {
        if (this == CONTAINER) {
            int slots = view.getTopInventory().getSize();
            return (view.getType() == InventoryType.CHEST || view.getType() == InventoryType.BARREL || view.getType() == InventoryType.ENDER_CHEST)
                && slots >= 9 && slots <= 54 && slots % 9 == 0;
        }
        return (view.getType() == inventoryType || this == DISPENSER && view.getType() == InventoryType.DROPPER)
            && view.getTopInventory().getSize() == size;
    }
    public boolean accepts(Recipe recipe) {
        return switch (this) {
            case CRAFTING -> recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe;
            case FURNACE -> recipe instanceof FurnaceRecipe;
            case BLAST_FURNACE -> recipe instanceof BlastingRecipe;
            case SMOKER -> recipe instanceof SmokingRecipe;
            case STONECUTTER -> recipe instanceof StonecuttingRecipe;
            case SMITHING -> recipe instanceof SmithingTransformRecipe || recipe instanceof SmithingTrimRecipe;
            case BREWING, MERCHANT, ANVIL, ENCHANTING, GRINDSTONE, LOOM, CARTOGRAPHY, BEACON, HOPPER, DISPENSER, CONTAINER, SHULKER_BOX -> false;
        };
    }
    public boolean visibleTo(Player player, Recipe recipe) {
        if (!accepts(recipe) || !(recipe instanceof org.bukkit.Keyed keyed)) return false;
        // Stonecutting and smithing do not require vanilla recipe-book discovery.
        return this == STONECUTTER || this == SMITHING || player.hasDiscoveredRecipe(keyed.getKey());
    }
}
