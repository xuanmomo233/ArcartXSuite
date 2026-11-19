package xuanmo.arcartxsuite.vanilla.recipe;

import org.bukkit.inventory.ItemStack;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;

/** Physical editing buffer. Configuration is the only persistent recipe representation. */
final class RecipeEditorSlots {
    private final ItemStack[] contents = new ItemStack[10];

    ItemStack get(int slot) { return InventoryItems.copy(contents[slot]); }
    void set(int slot, ItemStack item) {
        contents[slot] = InventoryItems.copy(item);
        if (slot < 9 && contents[slot] != null) contents[slot].setAmount(1);
    }
    void clear() { java.util.Arrays.fill(contents, null); }

    ItemStack click(int slot, int button, ItemStack carried) {
        ItemStack cursor = InventoryItems.copy(carried), current = get(slot);
        int limit = slot < 9 ? 1 : cursor != null ? cursor.getMaxStackSize()
            : current != null ? current.getMaxStackSize() : 64;
        if (cursor == null) {
            if (current == null) return null;
            int take = button == 1 ? (current.getAmount() + 1) / 2 : current.getAmount();
            cursor = current.clone();
            cursor.setAmount(take);
            current.setAmount(current.getAmount() - take);
            contents[slot] = InventoryItems.copy(current);
            return cursor;
        }
        if (current == null || current.isSimilar(cursor)) {
            int present = current == null ? 0 : current.getAmount();
            int move = Math.min(button == 1 ? 1 : cursor.getAmount(), Math.max(0, limit - present));
            if (move > 0) {
                contents[slot] = cursor.clone();
                contents[slot].setAmount(present + move);
                cursor.setAmount(cursor.getAmount() - move);
            }
            return InventoryItems.copy(cursor);
        }
        if (cursor.getAmount() <= limit) {
            contents[slot] = cursor;
            return current;
        }
        return cursor;
    }
}
