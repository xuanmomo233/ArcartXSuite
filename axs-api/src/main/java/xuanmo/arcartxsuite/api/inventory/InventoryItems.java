package xuanmo.arcartxsuite.api.inventory;

import java.util.Arrays;
import org.bukkit.inventory.ItemStack;

/** Inventory stack operations on a detached copy. */
public final class InventoryItems {
    private InventoryItems() { }

    public static boolean empty(ItemStack item) { return item == null || item.getType().isAir() || item.getAmount() <= 0; }
    public static ItemStack copy(ItemStack item) { return empty(item) ? null : item.clone(); }
    public static ItemStack[] copy(ItemStack[] items) { return Arrays.stream(items).map(InventoryItems::copy).toArray(ItemStack[]::new); }
    public static boolean put(ItemStack[] inventory, ItemStack item) {
        int left = item.getAmount();
        for (ItemStack stack : inventory) if (!empty(stack) && stack.isSimilar(item)) {
            int add = Math.min(left, Math.max(0, stack.getMaxStackSize() - stack.getAmount())); stack.setAmount(stack.getAmount()+add); left -= add; if (left == 0) return true;
        }
        for (int i = 0; i < inventory.length; i++) if (empty(inventory[i])) {
            int add = Math.min(left, item.getMaxStackSize()); inventory[i] = item.clone(); inventory[i].setAmount(add); left -= add; if (left == 0) return true;
        }
        return left == 0;
    }
}
